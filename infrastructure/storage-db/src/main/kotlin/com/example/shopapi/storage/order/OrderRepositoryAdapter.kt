package com.example.shopapi.storage.order

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.enums.OrderStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
internal class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    /**
     * 새 주문은 그대로 넣는다. 이미 있는 주문은 `status`/`updatedAt` 만 덮어쓴다 -
     * [Order] 가 생성 뒤에 바꿀 수 있는 것은 [Order.cancel] 이 만드는 상태 전이뿐이고,
     * 라인과 구매자는 불변이다.
     *
     * 취소 경로는 이 메서드를 쓰지 않는다. 동시 취소 경합은 [cancelIfPlaced] 의 조건부
     * 원자 갱신만 막을 수 있다(ADR 0016).
     */
    override fun save(order: Order): Order {
        val id = order.id ?: return jpaRepository.save(OrderJpaEntity.from(order)).toDomain()
        val entity = jpaRepository.findById(id).orElseThrow { OrderNotFoundException() }
        entity.status = order.status
        entity.updatedAt = order.updatedAt
        return jpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Order? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByBuyerId(
        buyerId: Long,
        cursor: Long?,
        size: Int,
    ): List<Order> {
        val page = PageRequest.of(0, size)
        val entities =
            if (cursor == null) {
                jpaRepository.findByBuyerIdOrderByIdDesc(buyerId, page)
            } else {
                jpaRepository.findByBuyerIdAndIdLessThanOrderByIdDesc(buyerId, cursor, page)
            }
        return entities.map { it.toDomain() }
    }

    override fun cancelIfPlaced(id: Long): Boolean =
        jpaRepository.cancelIfPlaced(id, OrderStatus.PLACED, OrderStatus.CANCELLED) == 1
}
