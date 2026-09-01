package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.ProductAvailability
import com.example.shopapi.core.enums.ProductCategory
import com.example.shopapi.core.enums.ProductStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * 상태 전이와 가용성 파생을 고정한다.
 *
 * 가용성을 컬럼으로 저장하지 않기로 한 결정(ADR 0014)이 지켜지는지도 여기서 본다 -
 * 재고만 바꿔도 노출 상태가 따라 바뀌어야 한다.
 */
class ProductTest {
    private val now = Instant.parse("2026-09-01T00:00:00Z")
    private val later = now.plusSeconds(60)

    private fun product(stock: Int = 10): Product =
        Product.register(
            name = ProductName.of("옥스퍼드 셔츠"),
            description = ProductDescription.of("면 100%"),
            price = Money.of(39_000),
            category = ProductCategory.FASHION,
            stockQuantity = StockQuantity.of(stock),
            now = now,
        )

    @Test
    fun `등록은 DRAFT 로 시작한다`() {
        val product = product()

        assertEquals(ProductStatus.DRAFT, product.status)
        assertEquals(ProductAvailability.UNAVAILABLE, product.availability)
        assertEquals(false, product.isPubliclyVisible)
    }

    @Test
    fun `판매를 시작하면 살 수 있다`() {
        val product = product().startSelling(later)

        assertEquals(ProductAvailability.ON_SALE, product.availability)
        assertEquals(true, product.isPubliclyVisible)
        assertEquals(later, product.updatedAt)
    }

    @Test
    fun `판매 중이어도 재고가 없으면 품절이다`() {
        val product = product(stock = 0).startSelling(later)

        assertEquals(ProductStatus.ON_SALE, product.status)
        assertEquals(ProductAvailability.SOLD_OUT, product.availability)
    }

    /** 이미 링크를 가진 사람에게 404 를 주면 "일시 중지"가 "없어졌다"로 읽힌다. */
    @Test
    fun `판매를 중지해도 상세는 열린다`() {
        val product = product().startSelling(later).suspendSelling(later)

        assertEquals(ProductAvailability.UNAVAILABLE, product.availability)
        assertEquals(true, product.isPubliclyVisible)
    }

    @Test
    fun `판매 개시는 멱등하다`() {
        val selling = product().startSelling(later)

        assertSame(selling, selling.startSelling(later.plusSeconds(60)))
    }

    /** 조용히 넘기면 호출자는 중지됐다고 믿는데 상태는 그대로다. */
    @Test
    fun `팔고 있지 않은 상품은 중지할 수 없다`() {
        assertFailsWith<ProductNotOnSaleException> { product().suspendSelling(later) }
    }

    @Test
    fun `단종은 되돌릴 수 없다`() {
        val discontinued = product().startSelling(later).discontinue(later)

        assertFailsWith<ProductDiscontinuedException> { discontinued.startSelling(later) }
        assertFailsWith<ProductDiscontinuedException> { discontinued.suspendSelling(later) }
        assertFailsWith<ProductDiscontinuedException> { discontinued.changePrice(Money.of(1000), later) }
        assertFailsWith<ProductDiscontinuedException> { discontinued.adjustStock(StockQuantity.of(1), later) }
    }

    @Test
    fun `단종 요청은 멱등하다`() {
        val discontinued = product().discontinue(later)

        assertSame(discontinued, discontinued.discontinue(later.plusSeconds(60)))
    }

    @Test
    fun `가격과 재고를 고치면 갱신 시각이 바뀐다`() {
        val changed = product().changePrice(Money.of(19_000), later)

        assertEquals(Money.of(19_000), changed.price)
        assertEquals(now, changed.createdAt)
        assertEquals(later, changed.updatedAt)
    }

    @Test
    fun `재고를 다시 세면 그 값으로 덮어쓴다`() {
        val adjusted = product(stock = 3).adjustStock(StockQuantity.of(50), later)

        assertEquals(50, adjusted.stockQuantity.value)
    }

    @Test
    fun `판매 중이고 재고가 충분할 때만 채울 수 있다고 답한다`() {
        val draft = product(stock = 5)
        val selling = draft.startSelling(later)

        assertEquals(false, draft.canFulfill(1))
        assertEquals(true, selling.canFulfill(5))
        assertEquals(false, selling.canFulfill(6))
        assertEquals(false, selling.canFulfill(0))
    }

    /** 로그에 상품이 찍혀도 설명 전문과 가격이 함께 새지 않아야 한다. */
    @Test
    fun `문자열 표현은 식별에 필요한 것만 담는다`() {
        val text = product().toString()

        assertEquals(true, text.contains("옥스퍼드 셔츠"))
        assertEquals(false, text.contains("39000"))
    }
}
