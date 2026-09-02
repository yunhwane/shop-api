package com.example.shopapi.api.order.application

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotCancellableException
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.port.TimeProvider
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
    @Transactional
    fun cancel(
        buyerId: Long,
        orderId: Long,
    ): Order {
        val order = orders.findById(orderId) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) {
            throw OrderNotFoundException()
        }

        val cancelled = order.cancel(timeProvider.now())
        if (!orders.cancelIfPlaced(orderId)) {
            throw OrderNotCancellableException()
        }

        cancelled.lines.forEach { line -> products.increaseStock(line.productId, line.quantity.value) }
        return cancelled
    }
}
