package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.PaymentStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PaymentTest {
    private val now = Instant.parse("2026-09-02T00:00:00Z")
    private val later = now.plusSeconds(60)

    private fun ready(amount: Long = 10_000): Payment =
        Payment.ready(
            orderId = 1L,
            tossOrderId = TossOrderId.of("ord-1-abcdefgh"),
            amount = Money.of(amount),
            now = now,
        )

    @Test
    fun `발급하면 READY 로 시작한다`() {
        val payment = ready()

        assertEquals(PaymentStatus.READY, payment.status)
        assertNull(payment.paymentKey)
    }

    @Test
    fun `금액이 다르면 거부한다`() {
        assertFailsWith<PaymentAmountMismatchException> { ready(amount = 10_000).verifyAmount(Money.of(9_999)) }
    }

    @Test
    fun `확정하면 DONE 이 되고 결제 키와 승인 시각을 담는다`() {
        val key = PaymentKey.of("payment-key")

        val confirmed = ready().confirm(key, later, later)

        assertEquals(PaymentStatus.DONE, confirmed.status)
        assertEquals(key, confirmed.paymentKey)
        assertEquals(later, confirmed.approvedAt)
    }

    @Test
    fun `이미 확정된 시도는 다시 확정할 수 없다`() {
        val confirmed = ready().confirm(PaymentKey.of("payment-key"), later, later)

        assertFailsWith<PaymentNotReadyException> {
            confirmed.confirm(PaymentKey.of("other-key"), later, later)
        }
    }

    @Test
    fun `실패 처리하면 FAILED 가 된다`() {
        val failed = ready().fail(later)

        assertEquals(PaymentStatus.FAILED, failed.status)
    }

    @Test
    fun `이미 끝난 시도는 다시 실패 처리할 수 없다`() {
        val failed = ready().fail(later)

        assertFailsWith<PaymentNotReadyException> { failed.fail(later.plusSeconds(60)) }
    }

    @Test
    fun `완료된 결제를 취소하면 CANCELLED 가 된다`() {
        val confirmed = ready().confirm(PaymentKey.of("payment-key"), later, later)

        val cancelled = confirmed.cancel(later.plusSeconds(60))

        assertEquals(PaymentStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `DONE 이 아닌 시도는 취소할 수 없다`() {
        assertFailsWith<PaymentNotCancellableException> { ready().cancel(later) }
    }
}
