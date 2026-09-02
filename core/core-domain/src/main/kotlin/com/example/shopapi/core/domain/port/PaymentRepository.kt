package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.payment.Payment
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import java.time.Instant

/**
 * 결제 시도 저장소.
 *
 * [markDoneIfReady] / [markFailedIfReady] 가 따로 있는 이유는 `OrderRepository.cancelIfPlaced`
 * 와 같다(ADR 0016, ADR 0017) - 상태 전이는 조건부 원자 갱신으로만 한다.
 */
interface PaymentRepository {
    /**
     * **새 결제 시도만 저장한다.** [Payment.id] 가 이미 있는 값을 받으면 구현은 실패해야 한다.
     *
     * 상태 전이(확정·실패)는 이 메서드를 거치지 않는다 - [markDoneIfReady] 를 쓴다.
     */
    fun save(payment: Payment): Payment

    fun findById(id: Long): Payment?

    fun findByOrderIdAndTossOrderId(
        orderId: Long,
        tossOrderId: TossOrderId,
    ): Payment?

    /**
     * `READY` 일 때만 `DONE` 으로 전이하며 [paymentKey]/[approvedAt] 을 함께 쓰고,
     * 전이했는지 알려준다.
     */
    fun markDoneIfReady(
        id: Long,
        paymentKey: PaymentKey,
        approvedAt: Instant,
        now: Instant,
    ): Boolean

    /** `READY` 일 때만 `FAILED` 로 전이하며 전이했는지 알려준다 */
    fun markFailedIfReady(
        id: Long,
        now: Instant,
    ): Boolean
}
