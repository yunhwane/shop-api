package com.example.shopapi.api.payment.dto

import com.example.shopapi.api.payment.application.ConfirmPaymentCommand
import com.example.shopapi.api.payment.application.ReadyPaymentResult

data class PaymentReadyResponse(
    val tossOrderId: String,
    val amount: Long,
    val orderName: String,
) {
    companion object {
        fun from(result: ReadyPaymentResult): PaymentReadyResponse =
            PaymentReadyResponse(
                tossOrderId = result.payment.tossOrderId.value,
                amount = result.payment.amount.amount,
                orderName = result.orderName,
            )
    }
}

data class ConfirmPaymentRequest(
    val tossOrderId: String,
    val paymentKey: String,
    val amount: Long,
) {
    fun toCommand(): ConfirmPaymentCommand = ConfirmPaymentCommand(tossOrderId, paymentKey, amount)
}
