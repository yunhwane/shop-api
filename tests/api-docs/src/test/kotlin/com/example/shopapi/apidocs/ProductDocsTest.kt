package com.example.shopapi.apidocs

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.Test

/**
 * 공개 카탈로그의 API 문서를 만든다.
 *
 * 동작 검증은 api 모듈의 `ProductCatalogTest` 가 맡는다. 여기서는 각 필드가 무엇인지를
 * 적는 데 집중한다.
 *
 * 시드를 끄고 이 테스트가 쓸 상품을 직접 등록한다. 문서에 실리는 응답이 실행 순서에
 * 따라 달라지면 안 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(properties = ["mail.provider=log", "catalog.seed=false"])
class ProductDocsTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `상품 목록을 조회한다`() {
        repeat(3) { onSaleProduct("문서용 셔츠 $it", ProductCategory.FASHION) }

        mockMvc
            .perform(
                get("/api/v1/products")
                    .param("category", "FASHION")
                    .param("keyword", "셔츠")
                    .param("sort", "LATEST")
                    .param("size", "2"),
            ).andDo(
                document(
                    "product-list",
                    preprocessResponse(prettyPrint()),
                    queryParameters(
                        parameterWithName("category").description("분류 필터. 생략하면 전체").optional(),
                        parameterWithName("keyword").description("상품명 부분 일치. 생략하면 전체").optional(),
                        parameterWithName("sort")
                            .description("LATEST(기본) / PRICE_ASC / PRICE_DESC")
                            .optional(),
                        parameterWithName("cursor")
                            .description("이전 응답의 nextCursor. 첫 쪽에서는 보내지 않는다")
                            .optional(),
                        parameterWithName("size").description("한 쪽의 개수. 1~100, 기본 20").optional(),
                    ),
                    responseFields(
                        fieldWithPath("data.items[].id").description("상품 식별자"),
                        fieldWithPath("data.items[].name").description("상품명"),
                        fieldWithPath("data.items[].price").description("판매가. 원 단위 정수다"),
                        fieldWithPath("data.items[].category").description("분류"),
                        fieldWithPath("data.items[].availability")
                            .description("ON_SALE / SOLD_OUT. 재고에서 파생되며 저장된 값이 아니다"),
                        fieldWithPath("data.nextCursor")
                            .description("다음 쪽을 요청할 때 그대로 돌려보낸다. 마지막 쪽이면 null")
                            .optional(),
                        fieldWithPath("data.hasNext").description("다음 쪽이 있는가"),
                    ),
                ),
            )
    }

    @Test
    fun `상품 상세를 조회한다`() {
        val id = onSaleProduct("문서용 원두 1kg", ProductCategory.FOOD)

        mockMvc
            .perform(get("/api/v1/products/{id}", id))
            .andDo(
                document(
                    "product-detail",
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("id").description("상품 식별자")),
                    responseFields(
                        fieldWithPath("data.id").description("상품 식별자"),
                        fieldWithPath("data.name").description("상품명"),
                        fieldWithPath("data.description").description("상품 설명. 빈 문자열일 수 있다"),
                        fieldWithPath("data.price").description("판매가. 원 단위 정수다"),
                        fieldWithPath("data.category").description("분류"),
                        fieldWithPath("data.availability")
                            .description("ON_SALE / SOLD_OUT / UNAVAILABLE"),
                        fieldWithPath("data.stockQuantity").description("남은 재고 수량"),
                    ),
                ),
            )
    }

    @Test
    fun `공개하지 않은 상품은 없는 것과 같이 답한다`() {
        val draft = draftProduct("문서용 미공개 상품", ProductCategory.ETC)

        mockMvc
            .perform(get("/api/v1/products/{id}", draft))
            .andDo(
                document(
                    "error-product-not-found",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("type").description("문제 유형 URI"),
                        fieldWithPath("title").description("사람이 읽는 요약"),
                        fieldWithPath("status").description("HTTP 상태 코드"),
                        fieldWithPath("detail").description("이 요청에 한정된 설명"),
                        fieldWithPath("instance").description("요청 경로"),
                        fieldWithPath("code").description("PRODUCT_NOT_FOUND"),
                        fieldWithPath("timestamp").description("발생 시각"),
                    ),
                ),
            )
    }

    private fun draftProduct(
        name: String,
        category: ProductCategory,
    ): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand(name, "문서를 만들기 위한 상품이다.", 39_000, category, 40))
                .id,
        )

    private fun onSaleProduct(
        name: String,
        category: ProductCategory,
    ): Long = draftProduct(name, category).also { managementService.startSelling(it) }
}
