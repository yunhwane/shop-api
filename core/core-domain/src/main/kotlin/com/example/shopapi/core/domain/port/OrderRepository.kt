package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.order.Order
import java.time.Instant

/**
 * 주문 저장소.
 *
 * [cancelIfPlaced] 가 따로 있는 이유는 `ProductRepository.decreaseStockIfEnough` 와 같다
 * (ADR 0014). `save` 로 상태를 덮어쓰면 동시에 들어온 취소 요청 두 개가 둘 다 통과해
 * 재고를 두 번 복원하는 경합이 생긴다(ADR 0016).
 */
interface OrderRepository {
    /**
     * **새 주문만 저장한다.** [Order.id] 가 이미 있는 주문을 받으면 구현은 실패해야 한다.
     *
     * 상태 전이(취소)는 이 메서드를 거치지 않는다 - 그 경로를 열어 두면 언젠가 상태
     * 갱신에 쓰여 [cancelIfPlaced] 가 막아 둔 동시 취소 경합이 조용히 되살아난다.
     */
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
     * `PLACED` 일 때만 `CANCELLED` 로 전이하며 [now] 를 `updatedAt` 에 함께 쓰고,
     * 전이했는지 알려준다.
     *
     * 조회하고 판단해서 저장하는 방식으로는 동시 취소 요청이 둘 다 통과해 재고가 두 번
     * 복원된다. 호출자는 이 반환값이 `true` 일 때만 재고를 복원해야 한다.
     */
    fun cancelIfPlaced(
        id: Long,
        now: Instant,
    ): Boolean
}
