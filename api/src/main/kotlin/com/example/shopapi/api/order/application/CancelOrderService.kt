package com.example.shopapi.api.order.application

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotCancellableException
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.port.TimeProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 취소.
 *
 * 본인 주문이 아니면 [OrderNotFoundException] 으로 답한다. 순차 발급되는 주문 ID 로
 * 타인의 주문 존재 여부를 추측하지 못하게 하기 위해서다(ADR 0016).
 *
 * [Order.cancel] 과 [OrderRepository.cancelIfPlaced] 를 둘 다 거친다. 앞은 정확한
 * 실패 사유를 위해서고, 뒤는 동시 취소 요청 중 실제로 전이에 성공한 쪽만 재고를
 * 복원하게 하기 위해서다 - 그렇지 않으면 재고가 두 번 복원된다.
 */
@Service
class CancelOrderService(
    private val orders: OrderRepository,
    private val products: ProductRepository,
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
}
