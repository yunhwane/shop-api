package com.example.shopapi.api.payment

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 결제 발급·확정을 HTTP 로 훑는다.
 *
 * `payment.toss.provider=fake` 로 실제 Toss 호출 없이 항상 승인 성공하는 게이트웨이를 쓴다
 * (ADR 0017). Toss 승인 실패·타임아웃 경로는 이 테스트로는 재현하지 못한다 - 게이트웨이
 * 구현이 아니라 그 결과를 받은 뒤의 도메인 반응(FAILED 전이, 에러 응답)을 확인하는
 * 계층이기 때문이다.
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
class PaymentFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `결제를 발급하면 주문 총액과 상품명을 담은 tossOrderId 를 받는다`() {
        val token = signUpAndLogin("pay01", "pay01@example.com")
        val productId = onSaleProduct("결제 테스트 상품", price = 10_000, stock = 5)
        val orderId = placedOrderId(token, productId, 2)

        readyPayment(token, orderId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.amount").value(20_000))
            .andExpect(jsonPath("$.data.orderName").value("결제 테스트 상품"))
            .andExpect(jsonPath("$.data.tossOrderId").exists())
    }

    @Test
    fun `남의 주문에는 결제를 발급할 수 없다`() {
        val owner = signUpAndLogin("pay02", "pay02@example.com")
        val other = signUpAndLogin("pay03", "pay03@example.com")
        val productId = onSaleProduct("소유권 결제 상품", price = 1_000, stock = 5)
        val orderId = placedOrderId(owner, productId, 1)

        readyPayment(other, orderId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
    }

    @Test
    fun `결제를 확정하면 주문이 PAID 가 된다`() {
        val token = signUpAndLogin("pay04", "pay04@example.com")
        val productId = onSaleProduct("확정 테스트 상품", price = 5_000, stock = 5)
        val orderId = placedOrderId(token, productId, 1)
        val readyBody =
            readyPayment(token, orderId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")

        confirmPayment(token, orderId, tossOrderId, "fake-payment-key-$orderId", 5_000)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
    }

    @Test
    fun `확정을 두 번 요청해도 같은 결과를 멱등하게 돌려준다`() {
        val token = signUpAndLogin("pay05", "pay05@example.com")
        val productId = onSaleProduct("멱등 테스트 상품", price = 3_000, stock = 5)
        val orderId = placedOrderId(token, productId, 1)
        val readyBody =
            readyPayment(token, orderId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")

        confirmPayment(token, orderId, tossOrderId, "fake-payment-key-$orderId", 3_000).andExpect(status().isOk)

        confirmPayment(token, orderId, tossOrderId, "fake-payment-key-$orderId", 3_000)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
    }

    @Test
    fun `클라이언트가 보낸 금액이 다르면 거절한다`() {
        val token = signUpAndLogin("pay06", "pay06@example.com")
        val productId = onSaleProduct("금액 위조 테스트 상품", price = 10_000, stock = 5)
        val orderId = placedOrderId(token, productId, 1)
        val readyBody =
            readyPayment(token, orderId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")

        confirmPayment(token, orderId, tossOrderId, "fake-payment-key-$orderId", 1)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"))
    }

    @Test
    fun `없는 결제 시도를 확정하면 404`() {
        val token = signUpAndLogin("pay07", "pay07@example.com")
        val productId = onSaleProduct("없는 결제 테스트 상품", price = 1_000, stock = 5)
        val orderId = placedOrderId(token, productId, 1)

        confirmPayment(token, orderId, "ord-does-not-exist", "fake-payment-key-$orderId", 1_000)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
    }

    @Test
    fun `결제가 완료된 주문은 취소할 수 없다`() {
        val token = signUpAndLogin("pay08", "pay08@example.com")
        val productId = onSaleProduct("결제 후 취소 테스트 상품", price = 2_000, stock = 5)
        val orderId = placedOrderId(token, productId, 1)
        val readyBody =
            readyPayment(token, orderId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")
        confirmPayment(token, orderId, tossOrderId, "fake-payment-key-$orderId", 2_000).andExpect(status().isOk)

        mockMvc
            .perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"))
    }

    @Test
    fun `이미 결제된 주문에는 새 결제를 발급할 수 없다`() {
        val token = signUpAndLogin("pay09", "pay09@example.com")
        val productId = onSaleProduct("재발급 테스트 상품", price = 1_500, stock = 5)
        val orderId = placedOrderId(token, productId, 1)
        val readyBody =
            readyPayment(token, orderId)
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")
        confirmPayment(token, orderId, tossOrderId, "fake-payment-key-$orderId", 1_500).andExpect(status().isOk)

        readyPayment(token, orderId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"))
    }

    private fun readyPayment(
        token: String,
        orderId: Long,
    ) = mockMvc.perform(
        post("/api/v1/orders/{orderId}/payments", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
    )

    private fun confirmPayment(
        token: String,
        orderId: Long,
        tossOrderId: String,
        paymentKey: String,
        amount: Long,
    ) = mockMvc.perform(
        post("/api/v1/orders/{orderId}/payments/confirm", orderId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"tossOrderId":"$tossOrderId","paymentKey":"$paymentKey","amount":$amount}"""),
    )

    private fun placedOrderId(
        token: String,
        productId: Long,
        quantity: Int,
    ): Long {
        val body =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"items":[{"productId":$productId,"quantity":$quantity}]}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun draftProduct(
        name: String,
        price: Long,
        stock: Int,
    ): Long =
        requireNotNull(
            registrationService.register(RegisterProductCommand(name, "설명", price, ProductCategory.ETC, stock)).id,
        )

    private fun onSaleProduct(
        name: String,
        price: Long,
        stock: Int,
    ): Long = draftProduct(name, price, stock).also { managementService.startSelling(it) }

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
