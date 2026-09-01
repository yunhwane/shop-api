package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.ProductCategory
import com.example.shopapi.core.enums.ProductSort
import com.example.shopapi.core.enums.ProductStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProductSearchTest {
    private val now = Instant.parse("2026-09-01T00:00:00Z")

    private fun product(
        id: Long,
        price: Long,
    ): Product =
        Product(
            id = id,
            name = ProductName.of("상품 $id"),
            description = ProductDescription.EMPTY,
            price = Money.of(price),
            category = ProductCategory.ETC,
            stockQuantity = StockQuantity.of(1),
            status = ProductStatus.ON_SALE,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun `크기 상한을 넘으면 거부한다`() {
        ProductSearchCriteria.of(size = ProductSearchCriteria.MAX_SIZE)

        assertFailsWith<InvalidValueException> {
            ProductSearchCriteria.of(size = ProductSearchCriteria.MAX_SIZE + 1)
        }
        assertFailsWith<InvalidValueException> { ProductSearchCriteria.of(size = 0) }
    }

    @Test
    fun `빈 검색어는 조건이 아니다`() {
        assertNull(ProductSearchCriteria.of(keyword = "   ").keyword)
        assertEquals("셔츠", ProductSearchCriteria.of(keyword = " 셔츠 ").keyword)
    }

    /** 커서 안의 값은 그 정렬 기준의 위치라, 어긋난 채로는 어떤 답도 맞지 않는다. */
    @Test
    fun `커서와 정렬이 어긋나면 거부한다`() {
        val cursor = ProductCursor.of(ProductSort.PRICE_ASC, Money.of(1000), 10)

        assertFailsWith<InvalidValueException> {
            ProductSearchCriteria.of(sort = ProductSort.LATEST, cursor = cursor)
        }
    }

    @Test
    fun `가격 정렬 커서는 가격을 함께 담아야 한다`() {
        assertFailsWith<InvalidValueException> { ProductCursor.of(ProductSort.PRICE_ASC, null, 1) }
        assertFailsWith<InvalidValueException> { ProductCursor.of(ProductSort.LATEST, Money.of(1000), 1) }
    }

    @Test
    fun `한 개를 더 읽어 왔으면 다음 쪽이 있다`() {
        val fetched = (1L..3L).map { product(it, 1000 * it) }

        val page = ProductPage.of(fetched, ProductSort.LATEST, size = 2)

        assertEquals(2, page.items.size)
        assertEquals(true, page.hasNext)
        assertEquals(2L, assertNotNull(page.nextCursor).id)
    }

    @Test
    fun `더 읽어 온 것이 없으면 마지막 쪽이다`() {
        val fetched = (1L..2L).map { product(it, 1000 * it) }

        val page = ProductPage.of(fetched, ProductSort.LATEST, size = 2)

        assertEquals(2, page.items.size)
        assertEquals(false, page.hasNext)
        assertNull(page.nextCursor)
    }

    @Test
    fun `가격 정렬이면 다음 커서에 가격이 실린다`() {
        val fetched = (1L..3L).map { product(it, 1000 * it) }

        val cursor = assertNotNull(ProductPage.of(fetched, ProductSort.PRICE_ASC, size = 2).nextCursor)

        assertEquals(Money.of(2000), cursor.price)
        assertEquals(2L, cursor.id)
    }
}
