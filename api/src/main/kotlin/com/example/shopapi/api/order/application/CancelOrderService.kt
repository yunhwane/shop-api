package com.example.shopapi.api.order.application

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotCancellableException
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.payment.PaymentNotFoundException
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.PaymentGateway
import com.example.shopapi.core.domain.port.PaymentRepository
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.enums.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 주문 취소.
 *
 * 본인 주문이 아니면 [OrderNotFoundException] 으로 답한다. 순차 발급되는 주문 ID 로
 * 타인의 주문 존재 여부를 추측하지 못하게 하기 위해서다(ADR 0016).
 *
 * [Order.cancel] 로 사전 검사부터 하고, 읽어온 시점의 상태로 두 갈래로 나뉜다(ADR 0018).
 * `PLACED` 였다면 기존과 같이 [OrderRepository.cancelIfPlaced] + 재고 복원이다. `PAID`
 * 였다면 [PaymentGateway.cancel] 로 PG 환불을 먼저 부르고, 성공했을 때만
 * [OrderRepository.cancelIfPaid] 로 넘어간다 - 재고는 복원하지 않는다.
 */
@Service
class CancelOrderService(
    private val orders: OrderRepository,
    private val products: ProductRepository,
    private val payments: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val timeProvider: TimeProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun cancel(
        buyerId: Long,
        orderId: Long,
    ): Order {
        val order = orders.findById(orderId) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) {
            throw OrderNotFoundException()
        }

        val now = timeProvider.now()
        val cancelled = order.cancel(now)

        return if (order.status == OrderStatus.PAID) {
            cancelPaid(orderId, cancelled, now)
        } else {
            cancelPlaced(orderId, cancelled, now)
        }
    }

    private fun cancelPlaced(
        orderId: Long,
        cancelled: Order,
        now: Instant,
    ): Order {
        if (!orders.cancelIfPlaced(orderId, now)) {
            throw OrderNotCancellableException()
        }

        cancelled.lines.forEach { line ->
            if (!products.increaseStock(line.productId, line.quantity.value)) {
                // 오늘은 일어날 수 없다 - 상품 행은 지우지 않는다(ADR 0014). 그래도 재고가
                // 조용히 어긋나면 안 되므로, 그 사실이 드러나게 남겨 둔다.
                log.warn(
                    "주문 취소로 재고를 복원하지 못했다. orderId={}, productId={}, quantity={}",
                    orderId,
                    line.productId,
                    line.quantity.value,
                )
            }
        }
        return cancelled
    }

    /**
     * PG 환불을 먼저 부르고 로컬 상태는 그 다음에 바꾼다 - [ConfirmPaymentService] 가
     * Toss 승인을 먼저 부르는 것과 같은 순서다(ADR 0017, ADR 0018). 환불이 실패하면
     * 이 메서드는 아무 것도 쓰지 않은 채로 예외를 그대로 전파한다.
     */
    private fun cancelPaid(
        orderId: Long,
        cancelled: Order,
        now: Instant,
    ): Order {
        val payment =
            payments.findDoneByOrderId(orderId) ?: run {
                // PAID 인데 완료된 결제가 없다 - 있을 수 없는 데이터 정합성 문제다.
                log.error("PAID 주문에 완료된 결제가 없다. 수동 확인이 필요하다. orderId={}", orderId)
                throw PaymentNotFoundException()
            }
        val paymentId = requireNotNull(payment.id)
        val paymentKey = requireNotNull(payment.paymentKey) { "DONE 결제는 paymentKey 를 갖는다" }

        paymentGateway.cancel(paymentKey, CANCEL_REASON)

        if (!payments.markCancelledIfDone(paymentId, now)) {
            log.error(
                "PG 환불은 됐지만 결제 상태를 CANCELLED 로 전이하지 못했다. 수동 확인이 필요하다. paymentId={}",
                paymentId,
            )
        }
        if (!orders.cancelIfPaid(orderId, now)) {
            log.error(
                "PG 환불은 됐지만 주문을 CANCELLED 로 전이하지 못했다. 수동 확인이 필요하다. orderId={}",
                orderId,
            )
            return requireNotNull(orders.findById(orderId))
        }
        return cancelled
    }

    companion object {
        private const val CANCEL_REASON = "구매자 요청에 의한 취소"
    }
}
