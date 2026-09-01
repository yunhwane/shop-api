package com.example.shopapi.api.product

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 공개 카탈로그를 HTTP 로 훑는다.
 *
 * 테스트마다 다른 카테고리를 쓰고 조회에도 그 카테고리를 건다. H2 를 테스트끼리
 * 공유하므로, 필터로 격리하지 않으면 다른 테스트가 만든 상품이 결과에 섞여
 * 실행 순서에 따라 답이 달라진다.
 *
 * 요청에 토큰을 붙이지 않는다. 카탈로그가 공개라는 사실이 이 테스트 전체로 확인된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        // 시드가 들어오면 목록 결과가 테스트가 만든 것과 섞인다.
        "catalog.seed=false",
    ],
)
class ProductCatalogTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `판매 중인 상품만 목록에 나온다`() {
        val category = ProductCategory.BEAUTY
        val onSale = onSaleProduct("판매중", category)
        val draft = draftProduct("작성중", category)
        val suspended = onSaleProduct("중지됨", category).also { managementService.suspendSelling(it) }
        val discontinued = onSaleProduct("단종됨", category).also { managementService.discontinue(it) }

        val ids = listIds(category)

        assertEquals(listOf(onSale), ids, "판매 중이 아닌 $draft, $suspended, $discontinued 는 빠져야 한다")
    }

    @Test
    fun `품절이어도 목록에 남는다`() {
        val category = ProductCategory.FOOD
        onSaleProduct("품절 상품", category, stock = 0)

        list(category)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].availability").value("SOLD_OUT"))
    }

    /** 커서 방식을 고른 이유가 이것이다. 목록이 움직여도 중복되거나 건너뛰지 않는다(ADR 0015). */
    @Test
    fun `커서로 이어 받으면 중복도 누락도 없다`() {
        val category = ProductCategory.BOOKS
        val created = (1..5).map { onSaleProduct("책 $it", category) }

        val collected = mutableListOf<Long>()
        var cursor: String? = null
        do {
            val response = list(category, size = 2, cursor = cursor).andExpect(status().isOk).andReturn()
            val body = response.response.contentAsString
            collected += idsIn(body)
            cursor = cursorIn(body)
        } while (cursor != null)

        // 기본 정렬은 최신순이므로 등록의 역순이다
        assertEquals(created.reversed(), collected)
    }

    /** 가격만 비교하면 동점 구간에서 항목이 통째로 빠지거나 반복된다. */
    @Test
    fun `가격이 같아도 페이지 경계가 흔들리지 않는다`() {
        val category = ProductCategory.SPORTS
        val cheap =
            listOf(onSaleProduct("싼 것 1", category, price = 1_000), onSaleProduct("싼 것 2", category, price = 1_000))
        val pricey =
            listOf(onSaleProduct("비싼 것 1", category, price = 2_000), onSaleProduct("비싼 것 2", category, price = 2_000))

        val collected = mutableListOf<Long>()
        var cursor: String? = null
        do {
            val body =
                list(category, size = 2, cursor = cursor, sort = "PRICE_ASC")
                    .andExpect(status().isOk)
                    .andReturn()
                    .response.contentAsString
            collected += idsIn(body)
            cursor = cursorIn(body)
        } while (cursor != null)

        assertEquals(cheap + pricey, collected)
    }

    @Test
    fun `커서와 정렬이 어긋나면 거절한다`() {
        val category = ProductCategory.DIGITAL
        repeat(3) { onSaleProduct("기기 $it", category) }

        val cursor =
            assertNotNull(
                cursorIn(list(category, size = 1, sort = "PRICE_ASC").andReturn().response.contentAsString),
            )

        list(category, size = 1, cursor = cursor, sort = "LATEST")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.errors[0].field").value("cursor"))
    }

    @Test
    fun `깨진 커서를 거절한다`() {
        list(cursor = "not-a-cursor!!")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("cursor"))
    }

    @Test
    fun `한 번에 가져갈 수 있는 개수를 제한한다`() {
        list(size = 1000)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("size"))
    }

    /** 이스케이프하지 않으면 "50%" 를 찾는 사용자가 와일드카드를 보낸 셈이 된다. */
    @Test
    fun `검색어의 와일드카드를 리터럴로 다룬다`() {
        val category = ProductCategory.LIVING
        val discounted = onSaleProduct("50% 할인 러그", category)
        onSaleProduct("일반 러그", category)

        val body =
            list(category, keyword = "%")
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        assertEquals(listOf(discounted), idsIn(body))
    }

    @Test
    fun `판매를 중지한 상품도 상세는 열린다`() {
        val suspended = onSaleProduct("중지된 상세", ProductCategory.ETC).also { managementService.suspendSelling(it) }

        mockMvc
            .perform(get("/api/v1/products/{id}", suspended))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.availability").value("UNAVAILABLE"))
    }

    @Test
    fun `공개하지 않은 상품은 없는 것과 같은 응답을 준다`() {
        val draft = draftProduct("숨은 상품", ProductCategory.ETC)
        val discontinued = onSaleProduct("사라진 상품", ProductCategory.ETC).also { managementService.discontinue(it) }

        listOf(draft, discontinued, 999_999L).forEach { id ->
            mockMvc
                .perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
        }
    }

    @Test
    fun `상세는 재고 수량을 그대로 알려준다`() {
        val product = onSaleProduct("재고 표시", ProductCategory.ETC, stock = 7)

        mockMvc
            .perform(get("/api/v1/products/{id}", product))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.stockQuantity").value(7))
            .andExpect(jsonPath("$.data.availability").value("ON_SALE"))
    }

    @Test
    fun `마지막 쪽에는 다음 커서가 없다`() {
        val body =
            list(ProductCategory.ETC, size = 100)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        assertNull(cursorIn(body))
    }

    private fun draftProduct(
        name: String,
        category: ProductCategory,
        price: Long = 10_000,
        stock: Int = 10,
    ): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand(name, "설명", price, category, stock))
                .id,
        )

    private fun onSaleProduct(
        name: String,
        category: ProductCategory,
        price: Long = 10_000,
        stock: Int = 10,
    ): Long = draftProduct(name, category, price, stock).also { managementService.startSelling(it) }

    private fun list(
        category: ProductCategory? = null,
        keyword: String? = null,
        sort: String? = null,
        cursor: String? = null,
        size: Int? = null,
    ): ResultActions =
        mockMvc.perform(
            get("/api/v1/products").apply {
                category?.let { param("category", it.name) }
                keyword?.let { param("keyword", it) }
                sort?.let { param("sort", it) }
                cursor?.let { param("cursor", it) }
                size?.let { param("size", it.toString()) }
            },
        )

    private fun listIds(category: ProductCategory): List<Long> =
        idsIn(
            list(category)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString,
        )

    private fun idsIn(body: String): List<Long> =
        Regex("\"id\":(\\d+)").findAll(body).map { it.groupValues[1].toLong() }.toList()

    private fun cursorIn(body: String): String? = Regex("\"nextCursor\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
}
