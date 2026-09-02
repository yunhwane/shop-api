package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.CorruptedDataException
import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.product.ProductName
import com.example.shopapi.core.domain.shipping.AddressLine1
import com.example.shopapi.core.domain.shipping.PhoneNumber
import com.example.shopapi.core.domain.shipping.PostalCode
import com.example.shopapi.core.domain.shipping.RecipientName
import com.example.shopapi.core.domain.shipping.ShippingAddress
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

    private val address =
        ShippingAddress(
            recipientName = RecipientName.of("전윤환"),
            phone = PhoneNumber.of("010-1234-5678"),
            postalCode = PostalCode.of("04524"),
            addressLine1 = AddressLine1.of("서울 중구 세종대로 110"),
            addressLine2 = null,
        )

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

    private fun place(
        lines: List<OrderLine> = listOf(line()),
        at: Instant = now,
    ): Order = Order.place(buyerId = 1L, lines = lines, shippingAddress = address, now = at)

    @Test
    fun `라인이 없으면 거부한다`() {
        assertFailsWith<InvalidValueException> { place(lines = emptyList()) }
    }

    @Test
    fun `같은 상품을 중복해서 담으면 거부한다`() {
        assertFailsWith<InvalidValueException> {
            place(lines = listOf(line(productId = 1L), line(productId = 1L)))
        }
    }

    @Test
    fun `주문하면 PLACED 로 시작한다`() {
        val order = place()

        assertEquals(OrderStatus.PLACED, order.status)
        assertEquals(now, order.createdAt)
        assertEquals(now, order.updatedAt)
    }

    /** 배송지는 주문할 때 받아 그대로 든다 - 주소록이 없다(ADR 0020) */
    @Test
    fun `주문은 입력받은 배송지를 그대로 든다`() {
        assertEquals(address, place().shippingAddress)
    }

    /**
     * 재고를 건드리기 전에 걸려야 한다. `totalAmount` 가 지연 계산이라 응답을 만들 때야
     * 처음 읽으면, 재고 차감과 저장이 이미 끝난 뒤에 실패한다.
     */
    @Test
    fun `총액이 상한을 넘으면 재고를 건드리기 전에 거부한다`() {
        assertFailsWith<InvalidValueException> {
            place(lines = listOf(line(price = 999_999_999, quantity = 2)))
        }
    }

    @Test
    fun `총액은 라인 합이다`() {
        val order =
            place(
                lines =
                    listOf(
                        line(productId = 1L, price = 10_000, quantity = 2),
                        line(productId = 2L, price = 5_000, quantity = 3),
                    ),
            )

        assertEquals(Money.of(35_000), order.totalAmount)
    }

    @Test
    fun `취소하면 상태와 갱신 시각이 바뀐다`() {
        val cancelled = place().cancel(later)

        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertEquals(now, cancelled.createdAt)
        assertEquals(later, cancelled.updatedAt)
    }

    @Test
    fun `이미 취소된 주문은 다시 취소할 수 없다`() {
        val cancelled = place().cancel(later)

        assertFailsWith<OrderNotCancellableException> { cancelled.cancel(later.plusSeconds(60)) }
    }

    @Test
    fun `결제하면 상태와 갱신 시각이 바뀐다`() {
        val paid = place().pay(later)

        assertEquals(OrderStatus.PAID, paid.status)
        assertEquals(now, paid.createdAt)
        assertEquals(later, paid.updatedAt)
    }

    @Test
    fun `이미 결제된 주문은 다시 결제할 수 없다`() {
        val paid = place().pay(later)

        assertFailsWith<OrderNotPayableException> { paid.pay(later.plusSeconds(60)) }
    }

    /** 결제완료 주문의 취소는 환불을 동반한다 - 주문 상태로는 결제 전 취소와 같다(ADR 0018) */
    @Test
    fun `결제 완료 주문도 취소할 수 있다`() {
        val paid = place().pay(later)

        val cancelled = paid.cancel(later.plusSeconds(60))

        assertEquals(OrderStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `취소된 주문은 결제할 수 없다`() {
        val cancelled = place().cancel(later)

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
                shippingAddress = address,
                status = OrderStatus.PLACED,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
