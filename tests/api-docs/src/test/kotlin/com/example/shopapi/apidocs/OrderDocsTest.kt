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
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 주문 생성·조회·취소의 API 문서를 만든다. 동작 검증은 api 모듈의 `OrderFlowTest` 가 맡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "catalog.seed=false",
        "security.bcrypt.strength=4",
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
    ],
)
class OrderDocsTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `주문을 생성한다`() {
        val token = signUpAndLogin("docsorder1", "docs-order1@example.com")
        val productId = onSaleProduct("문서용 원두 1kg")

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"productId":$productId,"quantity":2}]}"""),
            ).andDo(
                document(
                    "order-place",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    requestFields(
                        fieldWithPath("items[].productId").description("담을 상품 식별자"),
                        fieldWithPath("items[].quantity").description("수량. 1~100"),
                    ),
                    responseFields(orderFields()),
                ),
            )
    }

    @Test
    fun `주문 상세를 조회한다`() {
        val token = signUpAndLogin("docsorder2", "docs-order2@example.com")
        val orderId = placeOrder(token, onSaleProduct("문서용 셔츠"))

        mockMvc
            .perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andDo(
                document(
                    "order-detail",
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    pathParameters(parameterWithName("id").description("주문 식별자")),
                    responseFields(orderFields()),
                ),
            )
    }

    @Test
    fun `내 주문 목록을 조회한다`() {
        val token = signUpAndLogin("docsorder3", "docs-order3@example.com")
        val productId = onSaleProduct("문서용 목록 상품")
        repeat(3) { placeOrder(token, productId) }

        mockMvc
            .perform(
                get("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .param("size", "2"),
            ).andDo(
                document(
                    "order-list",
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    queryParameters(
                        parameterWithName("cursor")
                            .description("이전 응답의 nextCursor. 첫 쪽에서는 보내지 않는다")
                            .optional(),
                        parameterWithName("size").description("한 쪽의 개수. 1~100, 기본 20").optional(),
                    ),
                    responseFields(
                        *orderFields("data.items[]").toTypedArray(),
                        fieldWithPath("data.nextCursor")
                            .description("다음 쪽을 요청할 때 그대로 돌려보낸다. 마지막 쪽이면 null")
                            .optional(),
                        fieldWithPath("data.hasNext").description("다음 쪽이 있는가"),
                    ),
                ),
            )
    }

    @Test
    fun `주문을 취소한다`() {
        val token = signUpAndLogin("docsorder4", "docs-order4@example.com")
        val orderId = placeOrder(token, onSaleProduct("문서용 취소 상품"))

        mockMvc
            .perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andDo(
                document(
                    "order-cancel",
                    preprocessResponse(prettyPrint()),
                    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`")),
                    pathParameters(parameterWithName("id").description("주문 식별자")),
                    responseFields(orderFields()),
                ),
            )
    }

    @Test
    fun `재고가 부족하면 주문을 거절한다`() {
        val token = signUpAndLogin("docsorder5", "docs-order5@example.com")
        val productId = onSaleProduct("문서용 품절 상품", stock = 1)

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"productId":$productId,"quantity":2}]}"""),
            ).andDo(
                document(
                    "error-insufficient-stock",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("type").description("문제 유형 URI"),
                        fieldWithPath("title").description("사람이 읽는 요약"),
                        fieldWithPath("status").description("HTTP 상태 코드"),
                        fieldWithPath("detail").description("이 요청에 한정된 설명"),
                        fieldWithPath("instance").description("요청 경로"),
                        fieldWithPath("code").description("INSUFFICIENT_STOCK"),
                        fieldWithPath("timestamp").description("발생 시각"),
                    ),
                ),
            )
    }

    /** [prefix] 를 주면 목록 응답처럼 배열 원소 경로로도 쓸 수 있다 */
    private fun orderFields(prefix: String = "data") =
        listOf(
            fieldWithPath("$prefix.id").description("주문 식별자"),
            fieldWithPath("$prefix.status").description("PLACED / CANCELLED"),
            fieldWithPath("$prefix.totalAmount").description("라인 합계. 원 단위 정수다"),
            fieldWithPath("$prefix.lines[].productId").description("상품 식별자"),
            fieldWithPath("$prefix.lines[].productName").description("주문 시점의 상품명 스냅샷"),
            fieldWithPath("$prefix.lines[].unitPrice").description("주문 시점의 단가 스냅샷"),
            fieldWithPath("$prefix.lines[].quantity").description("수량"),
            fieldWithPath("$prefix.lines[].lineTotal").description("단가 × 수량"),
            fieldWithPath("$prefix.createdAt").description("주문 시각"),
        )

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

    private fun draftProduct(
        name: String,
        stock: Int = 10,
    ): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand(name, "문서를 만들기 위한 상품이다.", 39_000, ProductCategory.FOOD, stock))
                .id,
        )

    private fun onSaleProduct(
        name: String,
        stock: Int = 10,
    ): Long = draftProduct(name, stock).also { managementService.startSelling(it) }

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
