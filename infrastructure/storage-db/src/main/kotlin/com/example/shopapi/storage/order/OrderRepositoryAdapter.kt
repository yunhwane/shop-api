package com.example.shopapi.storage.order

import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.enums.OrderStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
internal class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    /**
     * **새 주문만 저장한다.** 이미 저장된 주문(`id` 가 있는 주문)을 받으면 실패한다.
     *
     * 상태 전이는 이 경로를 쓰지 않는다 - 여기서 받아 주면 언젠가 취소에도 쓰이게 되고,
     * 그러면 [cancelIfPlaced] 가 막아 둔 동시 취소 경합(재고 이중 복원)이 조용히
     * 되살아난다(ADR 0016). 안전해 보이는 일반화된 문을 열어 두지 않는다.
     */
    override fun save(order: Order): Order {
        check(order.id == null) { "이미 저장된 주문은 save 로 갱신할 수 없다. 상태 전이는 cancelIfPlaced 를 쓴다" }
        return jpaRepository.save(OrderJpaEntity.from(order)).toDomain()
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

    override fun cancelIfPlaced(
        id: Long,
        now: Instant,
    ): Boolean = jpaRepository.cancelIfPlaced(id, OrderStatus.PLACED, OrderStatus.CANCELLED, now) == 1
}
