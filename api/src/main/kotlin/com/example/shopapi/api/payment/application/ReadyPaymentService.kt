package com.example.shopapi.api.payment.application

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.payment.Payment
import com.example.shopapi.core.domain.payment.TossOrderId
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.PaymentRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.TokenGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 시도 발급.
 *
 * 본인 주문이 아니면 [OrderNotFoundException] 으로 답한다 - 주문 조회·취소와 같은
 * 소유권 규칙이다(ADR 0016).
 */
@Service
class ReadyPaymentService(
    private val orders: OrderRepository,
    private val payments: PaymentRepository,
    private val tokenGenerator: TokenGenerator,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun ready(
        buyerId: Long,
        orderId: Long,
    ): ReadyPaymentResult {
        val order = orders.findById(orderId) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) throw OrderNotFoundException()
        order.ensurePayable()

        val now = timeProvider.now()
        val payment = payments.save(Payment.ready(orderId, generateTossOrderId(orderId), order.totalAmount, now))
        return ReadyPaymentResult(payment, orderNameOf(order))
    }

    /** `Order.id` 를 그대로 쓰지 않는 이유는 [TossOrderId] 의 문서를 참고한다(ADR 0017) */
    private fun generateTossOrderId(orderId: Long): TossOrderId {
        val random = tokenGenerator.generate().replace("-", "")
        return TossOrderId.of("ord-$orderId-$random")
    }

    private fun orderNameOf(order: Order): String {
        val first =
            order.lines
                .first()
                .productName.value
        return if (order.lines.size == 1) first else "$first 외 ${order.lines.size - 1}건"
    }
}

data class ReadyPaymentResult(
    val payment: Payment,
    val orderName: String,
)
