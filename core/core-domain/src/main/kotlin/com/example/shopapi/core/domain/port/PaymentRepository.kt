package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.payment.Payment
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import java.time.Instant

/**
 * 결제 시도 저장소.
 *
 * [markDoneIfReady] / [markFailedIfReady] 가 따로 있는 이유는 `OrderRepository.cancelIfPlaced`
 * 와 같다(ADR 0016, ADR 0017) - 상태 전이는 조건부 원자 갱신으로만 한다. 같은 주문의
 * 서로 다른 결제 시도가 동시에 Toss 승인을 받는 경합은 이 저장소가 아니라
 * `OrderRepository.claimPaymentIfPlaced` 가 막는다(ADR 0019) - `Payment` 자신의 상태
 * 전이는 그 선점과 무관하게 그대로다.
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
     * 이 주문의 완료된(`DONE`) 결제 시도를 찾는다. 환불(ADR 0018)은 `tossOrderId` 를
     * 모르는 채로 주문 하나에 대한 결제를 찾아야 해서 [findByOrderIdAndTossOrderId] 로는
     * 안 된다.
     *
     * 정상 경로에서는 한 주문에 `DONE` 결제가 최대 하나다 - `ready` 는 주문이 `PLACED`
     * 일 때만 되므로, `PAID` 로 옮겨간 뒤에는 새 결제 시도가 생기지 않는다. 동시
     * `confirm` 두 개가 서로 다른 시도를 각각 `DONE` 으로 만드는 경합은
     * `OrderRepository.claimPaymentIfPlaced` 가 막지만(ADR 0019), 혹시라도 이 전제가
     * 깨지면 이 메서드는 그 중 하나만 돌려준다 - 나머지는 조용히 남는다.
     */
    fun findDoneByOrderId(orderId: Long): Payment?

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

    /** `DONE` 일 때만 `CANCELLED` 로 전이하며 전이했는지 알려준다(ADR 0018) */
    fun markCancelledIfDone(
        id: Long,
        now: Instant,
    ): Boolean
}
