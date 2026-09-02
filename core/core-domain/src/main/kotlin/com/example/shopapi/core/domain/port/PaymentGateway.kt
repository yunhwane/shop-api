package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import java.time.Instant

/**
 * PG 승인·취소 호출. 구현은 `infrastructure:client-payment-toss` 가 갖고 있고,
 * `api` 는 이 포트 뒤에서 어떤 PG 를 쓰는지 모른다(ADR 0004, ADR 0017, ADR 0018).
 */
interface PaymentGateway {
    /**
     * 결제를 승인 확정한다.
     *
     * 실패하면 [com.example.shopapi.core.domain.payment.PaymentConfirmFailedException] 을 던진다 -
     * 카드 한도 초과처럼 PG 가 거부한 경우와, 타임아웃처럼 통신 자체가 실패한 경우를 구분하지 않는다.
     * 둘 다 이 결제 시도는 끝난 것이고, 사용자는 다시 시도해야 한다.
     */
    fun confirm(
        paymentKey: PaymentKey,
        tossOrderId: TossOrderId,
        amount: Money,
    ): PaymentConfirmation

    /**
     * 결제를 전액 취소한다(ADR 0018). 부분 취소(금액 지정)는 다루지 않는다.
     *
     * 실패하면 [com.example.shopapi.core.domain.payment.PaymentCancelFailedException] 을
     * 던진다 - [confirm] 과 같이 PG 거부와 통신 실패를 구분하지 않는다.
     */
    fun cancel(
        paymentKey: PaymentKey,
        cancelReason: String,
    ): PaymentCancellation
}

data class PaymentConfirmation(
    val approvedAt: Instant,
)

data class PaymentCancellation(
    val canceledAt: Instant,
)
