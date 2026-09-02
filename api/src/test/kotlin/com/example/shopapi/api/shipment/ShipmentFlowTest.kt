package com.example.shopapi.api.shipment

import com.example.shopapi.api.order.SHIPPING_ADDRESS_JSON
import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.api.shipment.application.ShipmentTrackingService
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * 배송 생성·조회·추적을 훑는다(ADR 0020).
 *
 * 상태를 옮기는 쪽은 컨트롤러가 없어 HTTP 로 부를 수 없다 - `ShipmentTrackingService` 를
 * 직접 부른다. 이 테스트가 그 유스케이스의 유일한 호출자다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "catalog.seed=false",
        "payment.toss.provider=fake",
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.bcrypt.strength=4",
    ],
)
class ShipmentFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
    @param:Autowired private val trackingService: ShipmentTrackingService,
) {
    @Test
    fun `결제를 확정하면 배송이 준비중으로 생긴다`() {
        val token = signUpAndLogin("ship01", "ship01@example.com")
        val orderId = paidOrderId(token, "배송 생성 상품", 1_000)

        shipment(token, orderId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PREPARING"))
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.shippingAddress.recipientName").value("전윤환"))
            .andExpect(jsonPath("$.data.shippingAddress.addressLine2").value("5층"))
            .andExpect(jsonPath("$.data.shippedAt").doesNotExist())
    }

    @Test
    fun `결제 전 주문에는 배송이 없다`() {
        val token = signUpAndLogin("ship02", "ship02@example.com")
        val productId = onSaleProduct("배송 미생성 상품", 1_000)
        val orderId = placedOrderId(token, productId)

        shipment(token, orderId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SHIPMENT_NOT_FOUND"))
    }

    @Test
    fun `남의 주문 배송은 찾을 수 없다는 응답을 받는다`() {
        val owner = signUpAndLogin("ship03", "ship03@example.com")
        val other = signUpAndLogin("ship04", "ship04@example.com")
        val orderId = paidOrderId(owner, "소유권 배송 상품", 1_000)

        shipment(other, orderId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
    }

    @Test
    fun `발송하고 배송 완료하면 상태와 시각이 따라 바뀐다`() {
        val token = signUpAndLogin("ship05", "ship05@example.com")
        val orderId = paidOrderId(token, "배송 추적 상품", 2_000)

        trackingService.startShipping(orderId)

        shipment(token, orderId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("SHIPPING"))
            .andExpect(jsonPath("$.data.shippedAt").exists())
            .andExpect(jsonPath("$.data.deliveredAt").doesNotExist())

        trackingService.markDelivered(orderId)

        shipment(token, orderId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DELIVERED"))
            .andExpect(jsonPath("$.data.deliveredAt").exists())
    }

    /** 배송 상태는 건너뛸 수 없다 - 준비중인 배송이 곧바로 완료되면 추적이 거짓말을 한다 */
    @Test
    fun `준비 중인 배송은 곧바로 완료할 수 없다`() {
        val token = signUpAndLogin("ship06", "ship06@example.com")
        val orderId = paidOrderId(token, "건너뛰기 배송 상품", 1_500)

        assertFailsWith<IllegalStateException> { trackingService.markDelivered(orderId) }
    }

    private fun shipment(
        token: String,
        orderId: Long,
    ) = mockMvc.perform(
        get("/api/v1/orders/{orderId}/shipment", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
    )

    /** 결제까지 마친 주문을 만든다. 결제 확정이 배송을 만든다(ADR 0020) */
    private fun paidOrderId(
        token: String,
        productName: String,
        price: Long,
    ): Long {
        val productId = onSaleProduct(productName, price)
        val orderId = placedOrderId(token, productId)
        val readyBody =
            mockMvc
                .perform(
                    post("/api/v1/orders/{orderId}/payments", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")

        mockMvc
            .perform(
                post("/api/v1/orders/{orderId}/payments/confirm", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"tossOrderId":"$tossOrderId","paymentKey":"fake-key-$orderId","amount":$price}""",
                    ),
            ).andExpect(status().isOk)
        return orderId
    }

    private fun placedOrderId(
        token: String,
        productId: Long,
    ): Long {
        val body =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"items":[{"productId":$productId,"quantity":1}],$SHIPPING_ADDRESS_JSON}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun onSaleProduct(
        name: String,
        price: Long,
    ): Long {
        val id =
            requireNotNull(
                registrationService.register(RegisterProductCommand(name, "설명", price, ProductCategory.ETC, 5)).id,
            )
        managementService.startSelling(id)
        return id
    }

    private fun extract(
        body: String,
        field: String,
    ): String = Regex("\"$field\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    private fun signUpAndLogin(
        userId: String,
        email: String,
    ): String {
        val issued =
            mockMvc
                .perform(
                    post("/api/v1/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val verificationId = extract(issued, "verificationId")

        val verification = assertNotNull(verifications.findByVerificationId(VerificationId.of(verificationId)))

        mockMvc
            .perform(
                post("/api/v1/email-verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"${verification.token.value}"}"""),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"verificationId":"$verificationId","userId":"$userId","password":"$PASSWORD"}"""),
            ).andExpect(status().isCreated)

        val loginBody =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":"$userId","password":"$PASSWORD"}"""),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return extract(loginBody, "accessToken")
    }

    private companion object {
        const val PASSWORD = "password1"
    }
}
