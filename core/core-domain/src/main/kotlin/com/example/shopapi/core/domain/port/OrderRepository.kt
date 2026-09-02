package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.order.Order

/**
 * 주문 저장소.
 *
 * [cancelIfPlaced] 가 따로 있는 이유는 `ProductRepository.decreaseStockIfEnough` 와 같다
 * (ADR 0014). `save` 로 상태를 덮어쓰면 동시에 들어온 취소 요청 두 개가 둘 다 통과해
 * 재고를 두 번 복원하는 경합이 생긴다(ADR 0016).
 */
interface OrderRepository {
    fun save(order: Order): Order

    fun findById(id: Long): Order?

    /**
     * 이 구매자의 주문을 최신순으로 한 쪽 돌려준다.
     *
     * [cursor] 는 이전 쪽 마지막 주문의 [Order.id] 다. `null` 이면 첫 쪽이다.
     */
    fun findByBuyerId(
        buyerId: Long,
        cursor: Long?,
        size: Int,
    ): List<Order>

    /**
     * `PLACED` 일 때만 `CANCELLED` 로 전이하고, 전이했는지 알려준다.
     *
     * 조회하고 판단해서 저장하는 방식으로는 동시 취소 요청이 둘 다 통과해 재고가 두 번
     * 복원된다. 호출자는 이 반환값이 `true` 일 때만 재고를 복원해야 한다.
     */
    fun cancelIfPlaced(id: Long): Boolean
}
