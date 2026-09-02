package com.example.shopapi.api.order

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 주문 생성·조회·취소를 HTTP 로 훑는다.
 *
 * 소유권 검사(ADR 0016)를 확인하려면 서로 다른 두 회원이 필요하므로, 매 테스트가 아니라
 * 시나리오별로 필요한 만큼만 회원을 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "catalog.seed=false",
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.bcrypt.strength=4",
    ],
)
class OrderFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val products: ProductRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `토큰 없이는 주문할 수 없다`() {
        mockMvc
            .perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""{"items":[]}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `상품을 담아 주문하면 재고가 줄고 총액이 계산된다`() {
        val token = signUpAndLogin("order01", "order01@example.com")
        val productId = onSaleProduct("문서용 셔츠", price = 10_000, stock = 5)

        place(token, """{"items":[{"productId":$productId,"quantity":2}]}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("PLACED"))
            .andExpect(jsonPath("$.data.totalAmount").value(20_000))
            .andExpect(jsonPath("$.data.lines[0].productId").value(productId))
            .andExpect(jsonPath("$.data.lines[0].quantity").value(2))
            .andExpect(jsonPath("$.data.lines[0].lineTotal").value(20_000))

        assertEquals(3, stockOf(productId))
    }

    @Test
    fun `빈 주문은 거절한다`() {
        val token = signUpAndLogin("order02", "order02@example.com")

        place(token, """{"items":[]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.errors[0].field").value("items"))
    }

    @Test
    fun `같은 상품을 중복해서 담으면 거절한다`() {
        val token = signUpAndLogin("order03", "order03@example.com")
        val productId = onSaleProduct("중복 상품", price = 1_000, stock = 10)

        place(token, """{"items":[{"productId":$productId,"quantity":1},{"productId":$productId,"quantity":1}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.errors[0].field").value("items"))
    }

    @Test
    fun `없는 상품을 담으면 404`() {
        val token = signUpAndLogin("order04", "order04@example.com")

        place(token, """{"items":[{"productId":999999,"quantity":1}]}""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
    }

    @Test
    fun `판매 중이 아닌 상품을 담으면 409`() {
        val token = signUpAndLogin("order05", "order05@example.com")
        val productId = draftProduct("미공개 상품", price = 1_000, stock = 10)

        place(token, """{"items":[{"productId":$productId,"quantity":1}]}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ON_SALE"))
    }

    @Test
    fun `재고보다 많이 담으면 409 이고 재고는 그대로다`() {
        val token = signUpAndLogin("order06", "order06@example.com")
        val productId = onSaleProduct("품절 임박", price = 1_000, stock = 2)

        place(token, """{"items":[{"productId":$productId,"quantity":3}]}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))

        assertEquals(2, stockOf(productId), "실패한 주문이 재고를 건드리면 안 된다")
    }

    /** 두 라인 중 하나만 재고가 부족해도, 먼저 검증되는 라인이 있는 다른 상품의 재고까지 건드리면 안 된다. */
    @Test
    fun `여러 상품 중 하나라도 재고가 부족하면 아무 재고도 줄지 않는다`() {
        val token = signUpAndLogin("order07", "order07@example.com")
        val enough = onSaleProduct("충분한 상품", price = 1_000, stock = 5)
        val short = onSaleProduct("부족한 상품", price = 1_000, stock = 1)

        place(
            token,
            """{"items":[{"productId":$enough,"quantity":1},{"productId":$short,"quantity":5}]}""",
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))

        assertEquals(5, stockOf(enough), "함께 담긴 다른 상품의 재고가 줄면 안 된다")
        assertEquals(1, stockOf(short))
    }

    @Test
    fun `본인 주문은 조회할 수 있지만 남의 주문은 찾을 수 없다는 응답을 받는다`() {
        val owner = signUpAndLogin("order08", "order08@example.com")
        val other = signUpAndLogin("order09", "order09@example.com")
        val productId = onSaleProduct("소유권 테스트 상품", price = 1_000, stock = 5)
        val orderId = placedOrderId(owner, productId, 1)

        mockMvc
            .perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $owner"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(orderId))

        mockMvc
            .perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $other"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
    }

    @Test
    fun `취소하면 상태가 바뀌고 재고가 돌아온다`() {
        val token = signUpAndLogin("order10", "order10@example.com")
        val productId = onSaleProduct("취소 테스트 상품", price = 1_000, stock = 5)
        val orderId = placedOrderId(token, productId, 2)
        assertEquals(3, stockOf(productId))

        mockMvc
            .perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))

        assertEquals(5, stockOf(productId))
    }

    @Test
    fun `이미 취소된 주문은 다시 취소할 수 없다`() {
        val token = signUpAndLogin("order11", "order11@example.com")
        val productId = onSaleProduct("이중 취소 테스트 상품", price = 1_000, stock = 5)
        val orderId = placedOrderId(token, productId, 1)

        mockMvc
            .perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"))
    }

    @Test
    fun `남의 주문은 취소할 수 없다는 것조차 알려주지 않는다`() {
        val owner = signUpAndLogin("order12", "order12@example.com")
        val other = signUpAndLogin("order13", "order13@example.com")
        val productId = onSaleProduct("소유권 취소 테스트 상품", price = 1_000, stock = 5)
        val orderId = placedOrderId(owner, productId, 1)

        mockMvc
            .perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $other"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))

        assertEquals(4, stockOf(productId), "실패한 취소가 재고를 건드리면 안 된다")
    }

    @Test
    fun `내 주문 목록은 최신순으로 커서를 이어 받는다`() {
        val token = signUpAndLogin("order14", "order14@example.com")
        val productId = onSaleProduct("목록 테스트 상품", price = 1_000, stock = 100)
        val created = (1..5).map { placedOrderId(token, productId, 1) }

        val collected = mutableListOf<Long>()
        var cursor: String? = null
        do {
            val response =
                mockMvc
                    .perform(
                        get("/api/v1/orders")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                            .param("size", "2")
                            .apply { cursor?.let { param("cursor", it) } },
                    ).andExpect(status().isOk)
                    .andReturn()
            val body = response.response.contentAsString
            collected += idsIn(body)
            cursor = cursorIn(body)
        } while (cursor != null)

        assertEquals(created.reversed(), collected)
    }

    private fun place(
        token: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun placedOrderId(
        token: String,
        productId: Long,
        quantity: Int,
    ): Long {
        val body =
            place(token, """{"items":[{"productId":$productId,"quantity":$quantity}]}""")
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun stockOf(id: Long): Int = assertNotNull(products.findById(id)).stockQuantity.value

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

    private fun idsIn(body: String): List<Long> =
        Regex("\"items\":\\[(.*?)],\"nextCursor\"")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.let { itemsSection ->
                Regex("\"id\":(\\d+)").findAll(itemsSection).map { it.groupValues[1].toLong() }.toList()
            }
            ?: emptyList()

    private fun cursorIn(body: String): String? = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)

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

    private fun extract(
        body: String,
        field: String,
    ): String = Regex("\"$field\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    private companion object {
        const val PASSWORD = "password1"
    }
}
