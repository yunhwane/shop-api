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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 배송 조회의 API 문서를 만든다. 동작 검증은 api 모듈의 `ShipmentFlowTest` 가 맡는다.
 *
 * 배송 상태를 바꾸는 쪽은 컨트롤러가 없어 문서에도 나오지 않는다(ADR 0020).
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
class ShipmentDocsTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `배송을 조회한다`() {
        val token = signUpAndLogin("docsship1", "docs-ship1@example.com")
        val orderId = paidOrder(token, onSaleProduct("문서용 배송 상품"))

        mockMvc
            .perform(
                get("/api/v1/orders/{orderId}/shipment", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andDo(
                document(
                    "shipment-detail",
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    pathParameters(parameterWithName("orderId").description("주문 식별자")),
                    responseFields(
                        fieldWithPath("data.orderId").description("주문 식별자"),
                        fieldWithPath("data.status").description("PREPARING / SHIPPING / DELIVERED"),
                        fieldWithPath("data.shippingAddress.recipientName").description("수령인"),
                        fieldWithPath("data.shippingAddress.phone").description("연락처"),
                        fieldWithPath("data.shippingAddress.postalCode").description("우편번호"),
                        fieldWithPath("data.shippingAddress.addressLine1").description("기본주소"),
                        fieldWithPath("data.shippingAddress.addressLine2").description("상세주소. 없으면 null").optional(),
                        fieldWithPath("data.shippedAt").description("발송 시각. 아직 발송 전이면 null").optional(),
                        fieldWithPath("data.deliveredAt").description("배송 완료 시각. 완료 전이면 null").optional(),
                        fieldWithPath("data.createdAt").description("배송이 만들어진 시각. 결제 확정 시점이다"),
                    ),
                ),
            )
    }

    /** 결제를 확정해야 배송이 생긴다(ADR 0020) */
    private fun paidOrder(
        token: String,
        productId: Long,
    ): Long {
        val orderBody =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"items":[{"productId":$productId,"quantity":1}],""" +
                                """"shippingAddress":{"recipientName":"전윤환","phone":"010-1234-5678",""" +
                                """"postalCode":"04524","addressLine1":"서울 중구 세종대로 110","addressLine2":"5층"}}""",
                        ),
                ).andReturn()
                .response.contentAsString
        val orderId = Regex("\"id\":(\\d+)").find(orderBody)!!.groupValues[1].toLong()
        val amount = Regex("\"totalAmount\":(\\d+)").find(orderBody)!!.groupValues[1].toLong()

        val readyBody =
            mockMvc
                .perform(
                    post("/api/v1/orders/{orderId}/payments", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andReturn()
                .response.contentAsString
        val tossOrderId = extract(readyBody, "tossOrderId")

        mockMvc.perform(
            post("/api/v1/orders/{orderId}/payments/confirm", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tossOrderId":"$tossOrderId","paymentKey":"docs-key-$orderId","amount":$amount}"""),
        )
        return orderId
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
