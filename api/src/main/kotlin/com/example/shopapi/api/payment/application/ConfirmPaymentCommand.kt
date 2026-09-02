package com.example.shopapi.api.payment.application

/**
 * 결제 확정 입력. 클라이언트가 Toss 결제창 리다이렉트에서 받아온 값을 그대로 담는다.
 *
 * [amount] 는 신뢰하지 않는다 - [com.example.shopapi.core.domain.payment.Payment.verifyAmount]
 * 가 서버에 저장된 값과 대조한다(ADR 0017).
 */
data class ConfirmPaymentCommand(
    val tossOrderId: String,
    val paymentKey: String,
    val amount: Long,
)
