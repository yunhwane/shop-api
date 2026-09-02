package com.example.shopapi.api.order.application

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderLine
import com.example.shopapi.core.domain.order.OrderQuantity
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.product.InsufficientStockException
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 생성.
 *
 * 라인마다 "먼저 읽어서 정확한 사유로 검증 → 원자 쓰기로 최종 확정" 두 단계로 간다.
 * `ProductManagementService.adjustStock` 과 같은 구조다. 사전 검사를 통과한 뒤에도
 * 원자 차감이 실패하면 동시 주문에 재고를 뺏긴 것이라 같은 [InsufficientStockException]
 * 으로 답한다.
 *
 * [Order.place] 호출을 재고 차감보다 먼저 둔다. 빈 주문·중복 상품 검증이 거기서
 * 일어나므로, 검증에 실패했을 때 이미 차감된 재고가 남는 일을 막는다.
 *
 * 상품 조회는 라인마다 한 번씩이 아니라 [ProductRepository.findAllById] 로 한 번에
 * 읽는다. 재고 차감은 그렇게 묶을 수 없다 - 각 줄이 서로 다른 조건부 원자 갱신이다(ADR 0014).
 */
@Service
class PlaceOrderService(
    private val products: ProductRepository,
    private val orders: OrderRepository,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun place(
        buyerId: Long,
        command: PlaceOrderCommand,
    ): Order {
        val productById = products.findAllById(command.items.map { it.productId }).associateBy { it.id }
        val lines = command.items.map { toLine(it, productById[it.productId]) }
        val order = Order.place(buyerId = buyerId, lines = lines, now = timeProvider.now())

        command.items.forEach { item ->
            if (!products.decreaseStockIfEnough(item.productId, item.quantity)) {
                throw InsufficientStockException(item.productId)
            }
        }

        return orders.save(order)
    }

    private fun toLine(
        item: PlaceOrderItemCommand,
        product: Product?,
    ): OrderLine {
        val found = product ?: throw ProductNotFoundException()
        found.ensureOrderable(item.quantity)
        return OrderLine(
            productId = item.productId,
            productName = found.name,
            unitPrice = found.price,
            quantity = OrderQuantity.of(item.quantity),
        )
    }
}
