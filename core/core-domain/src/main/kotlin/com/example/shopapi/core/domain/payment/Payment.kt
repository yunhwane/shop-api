package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.PaymentStatus
import java.time.Instant

/**
 * 결제 시도 하나. `Order` 객체가 아니라 [orderId] 만 참조한다 - [OrderLine][com.example.shopapi.core.domain.order.OrderLine]
 * 이 상품을 스냅샷하는 것과 같은 이유로, 결제도 주문과 독립적인 생명주기를 갖는 애그리게이트다(ADR 0017).
 *
 * 결제 하나에 여러 번 시도가 붙을 수 있다(재시도). 이 중 하나가 [PaymentStatus.DONE] 이
 * 됐을 때만 주문이 `PAID` 로 옮겨간다.
 */
class Payment private constructor(
    val id: Long?,
    val orderId: Long,
    val tossOrderId: TossOrderId,
    val amount: Money,
    val status: PaymentStatus,
    val paymentKey: PaymentKey?,
    val approvedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * 클라이언트가 confirm 요청에 실어 보낸 금액을 신뢰하지 않고 서버가 기록해 둔 [amount]
     * 와 대조한다. Toss 승인을 부르기 전에 걸러야, 위조된 금액으로 시도된 확정이 외부
     * 호출 없이 바로 거부된다(ADR 0017).
     */
    fun verifyAmount(claimed: Money) {
        if (claimed != amount) {
            throw PaymentAmountMismatchException()
        }
    }

    /**
     * Toss 승인 성공 반영.
     *
     * [PaymentStatus.READY] 가 아니면 거부한다. 이미 `DONE` 인 시도를 같은 결과로
     * 멱등하게 다시 확정하는 판단은 호출자(애플리케이션 서비스)의 몫이다 - 여기서는
     * 값 객체 하나만 상태를 갖고 판단하므로 재시도 문맥(같은 paymentKey 인가)을 모른다.
     */
    fun confirm(
        paymentKey: PaymentKey,
        approvedAt: Instant,
        now: Instant,
    ): Payment {
        if (status != PaymentStatus.READY) {
            throw PaymentNotReadyException()
        }
        return Payment(id, orderId, tossOrderId, amount, PaymentStatus.DONE, paymentKey, approvedAt, createdAt, now)
    }

    /** Toss 승인 실패 반영. 이 결제 시도는 여기서 끝나고, 재시도는 새 [ready] 로 한다 */
    fun fail(now: Instant): Payment {
        if (status != PaymentStatus.READY) {
            throw PaymentNotReadyException()
        }
        return Payment(id, orderId, tossOrderId, amount, PaymentStatus.FAILED, paymentKey, approvedAt, createdAt, now)
    }

    override fun toString(): String = "Payment(id=$id, orderId=$orderId, status=$status)"

    companion object {
        /** 새 결제 시도. [amount] 는 호출자가 그 시점의 `Order.totalAmount` 를 스냅샷해서 넘긴다 */
        fun ready(
            orderId: Long,
            tossOrderId: TossOrderId,
            amount: Money,
            now: Instant,
        ): Payment =
            Payment(
                id = null,
                orderId = orderId,
                tossOrderId = tossOrderId,
                amount = amount,
                status = PaymentStatus.READY,
                paymentKey = null,
                approvedAt = null,
                createdAt = now,
                updatedAt = now,
            )

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(
            id: Long,
            orderId: Long,
            tossOrderId: TossOrderId,
            amount: Money,
            status: PaymentStatus,
            paymentKey: PaymentKey?,
            approvedAt: Instant?,
            createdAt: Instant,
            updatedAt: Instant,
        ): Payment = Payment(id, orderId, tossOrderId, amount, status, paymentKey, approvedAt, createdAt, updatedAt)
    }
}
