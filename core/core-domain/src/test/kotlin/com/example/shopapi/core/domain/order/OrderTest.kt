package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.CorruptedDataException
import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.product.ProductName
import com.example.shopapi.core.enums.OrderStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 상태 전이와 총액 파생을 고정한다.
 *
 * 총액을 컬럼으로 저장하지 않기로 한 결정(ADR 0016)이 지켜지는지도 여기서 본다 -
 * 라인이 바뀌면 총액도 따라 바뀌어야 한다.
 */
class OrderTest {
    private val now = Instant.parse("2026-09-02T00:00:00Z")
    private val later = now.plusSeconds(60)

    private fun line(
        productId: Long = 1L,
        price: Long = 10_000,
        quantity: Int = 2,
    ): OrderLine =
        OrderLine(
            productId = productId,
            productName = ProductName.of("상품 $productId"),
            unitPrice = Money.of(price),
            quantity = OrderQuantity.of(quantity),
        )

    @Test
    fun `라인이 없으면 거부한다`() {
        assertFailsWith<InvalidValueException> { Order.place(buyerId = 1L, lines = emptyList(), now = now) }
    }

    @Test
    fun `같은 상품을 중복해서 담으면 거부한다`() {
        assertFailsWith<InvalidValueException> {
            Order.place(buyerId = 1L, lines = listOf(line(productId = 1L), line(productId = 1L)), now = now)
        }
    }

    @Test
    fun `주문하면 PLACED 로 시작한다`() {
        val order = Order.place(buyerId = 1L, lines = listOf(line()), now = now)

        assertEquals(OrderStatus.PLACED, order.status)
        assertEquals(now, order.createdAt)
        assertEquals(now, order.updatedAt)
    }

    /**
     * 재고를 건드리기 전에 걸려야 한다. `totalAmount` 가 지연 계산이라 응답을 만들 때야
     * 처음 읽으면, 재고 차감과 저장이 이미 끝난 뒤에 실패한다.
     */
    @Test
    fun `총액이 상한을 넘으면 재고를 건드리기 전에 거부한다`() {
        assertFailsWith<InvalidValueException> {
            Order.place(buyerId = 1L, lines = listOf(line(price = 999_999_999, quantity = 2)), now = now)
        }
    }

    @Test
    fun `총액은 라인 합이다`() {
        val order =
            Order.place(
                buyerId = 1L,
                lines =
                    listOf(
                        line(productId = 1L, price = 10_000, quantity = 2),
                        line(productId = 2L, price = 5_000, quantity = 3),
                    ),
                now = now,
            )

        assertEquals(Money.of(35_000), order.totalAmount)
    }

    @Test
    fun `취소하면 상태와 갱신 시각이 바뀐다`() {
        val cancelled = Order.place(buyerId = 1L, lines = listOf(line()), now = now).cancel(later)

        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertEquals(now, cancelled.createdAt)
        assertEquals(later, cancelled.updatedAt)
    }

    @Test
    fun `이미 취소된 주문은 다시 취소할 수 없다`() {
        val cancelled = Order.place(buyerId = 1L, lines = listOf(line()), now = now).cancel(later)

        assertFailsWith<OrderNotCancellableException> { cancelled.cancel(later.plusSeconds(60)) }
    }

    @Test
    fun `결제하면 상태와 갱신 시각이 바뀐다`() {
        val paid = Order.place(buyerId = 1L, lines = listOf(line()), now = now).pay(later)

        assertEquals(OrderStatus.PAID, paid.status)
        assertEquals(now, paid.createdAt)
        assertEquals(later, paid.updatedAt)
    }

    @Test
    fun `이미 결제된 주문은 다시 결제할 수 없다`() {
        val paid = Order.place(buyerId = 1L, lines = listOf(line()), now = now).pay(later)

        assertFailsWith<OrderNotPayableException> { paid.pay(later.plusSeconds(60)) }
    }

    @Test
    fun `결제 완료 주문은 취소할 수 없다`() {
        val paid = Order.place(buyerId = 1L, lines = listOf(line()), now = now).pay(later)

        assertFailsWith<OrderNotCancellableException> { paid.cancel(later.plusSeconds(60)) }
    }

    @Test
    fun `취소된 주문은 결제할 수 없다`() {
        val cancelled = Order.place(buyerId = 1L, lines = listOf(line()), now = now).cancel(later)

        assertFailsWith<OrderNotPayableException> { cancelled.pay(later.plusSeconds(60)) }
    }

    /** 서버 데이터 문제를 클라이언트 입력 탓으로 돌리지 않는다(ADR 0007) */
    @Test
    fun `복원한 총액이 상한을 넘으면 저장된 값 문제로 답한다`() {
        assertFailsWith<CorruptedDataException> {
            Order.reconstitute(
                id = 1L,
                buyerId = 1L,
                lines = listOf(line(price = 999_999_999, quantity = 2)),
                status = OrderStatus.PLACED,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
