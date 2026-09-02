package com.example.shopapi.apidocs

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 결제 발급·확정의 API 문서를 만든다. 동작 검증은 api 모듈의 `PaymentFlowTest` 가 맡는다.
 *
 * `payment.toss.provider=fake` 로 실제 Toss 호출 없이 문서를 만든다(ADR 0017).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "catalog.seed=false",
        "payment.toss.provider=fake",
        "security.bcrypt.strength=4",
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
    ],
)
class PaymentDocsTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `결제를 발급한다`() {
        val token = signUpAndLogin("docspay1", "docs-pay1@example.com")
        val orderId = placeOrder(token, onSaleProduct("문서용 결제 상품"))

        mockMvc
            .perform(
                post("/api/v1/orders/{orderId}/payments", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andDo(
                document(
                    "payment-ready",
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    pathParameters(parameterWithName("orderId").description("주문 식별자")),
                    responseFields(
                        fieldWithPath("data.tossOrderId").description("Toss 결제창을 열 때 쓰는 결제 시도 식별자"),
                        fieldWithPath("data.amount").description("결제할 금액. 원 단위 정수다"),
                        fieldWithPath("data.orderName").description("결제창에 보여줄 주문 요약"),
                    ),
                ),
            )
    }

    @Test
    fun `결제를 확정한다`() {
        val token = signUpAndLogin("docspay2", "docs-pay2@example.com")
        val orderId = placeOrder(token, onSaleProduct("문서용 확정 상품"))
        val readyBody =
            mockMvc
                .perform(
                    post("/api/v1/orders/{orderId}/payments", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")

        mockMvc
            .perform(
                post("/api/v1/orders/{orderId}/payments/confirm", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"tossOrderId":"$tossOrderId","paymentKey":"docs-payment-key","amount":39000}""",
                    ),
            ).andDo(
                document(
                    "payment-confirm",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    pathParameters(parameterWithName("orderId").description("주문 식별자")),
                    requestFields(
                        fieldWithPath("tossOrderId").description("발급 시 받은 결제 시도 식별자"),
                        fieldWithPath("paymentKey").description("Toss 결제창이 돌려준 결제 키"),
                        fieldWithPath("amount")
                            .description("결제창에서 승인된 금액. 서버가 기록해 둔 금액과 다르면 거절한다"),
                    ),
                    responseFields(
                        fieldWithPath("data.id").description("주문 식별자"),
                        fieldWithPath("data.status").description("PLACED / PAID / CANCELLED"),
                        fieldWithPath("data.totalAmount").description("라인 합계. 원 단위 정수다"),
                        fieldWithPath("data.lines[].productId").description("상품 식별자"),
                        fieldWithPath("data.lines[].productName").description("주문 시점의 상품명 스냅샷"),
                        fieldWithPath("data.lines[].unitPrice").description("주문 시점의 단가 스냅샷"),
                        fieldWithPath("data.lines[].quantity").description("수량"),
                        fieldWithPath("data.lines[].lineTotal").description("단가 × 수량"),
                        fieldWithPath("data.createdAt").description("주문 시각"),
                    ),
                ),
            )
    }

    private fun placeOrder(
        token: String,
        productId: Long,
    ): Long {
        val body =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"items":[{"productId":$productId,"quantity":1}]}"""),
                ).andReturn()
                .response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun draftProduct(name: String): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand(name, "문서를 만들기 위한 상품이다.", 39_000, ProductCategory.FOOD, 10))
                .id,
        )

    private fun onSaleProduct(name: String): Long = draftProduct(name).also { managementService.startSelling(it) }

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
                ).andReturn()
                .response.contentAsString
        val verificationId = extract(issued, "verificationId")

        val verification = assertNotNull(verifications.findByVerificationId(VerificationId.of(verificationId)))

        mockMvc.perform(
            post("/api/v1/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${verification.token.value}"}"""),
        )
        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"$verificationId","userId":"$userId","password":"$PASSWORD"}"""),
        )

        val loginBody =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":"$userId","password":"$PASSWORD"}"""),
                ).andReturn()
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
