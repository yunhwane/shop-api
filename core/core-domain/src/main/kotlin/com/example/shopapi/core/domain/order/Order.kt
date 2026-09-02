package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.OrderStatus
import java.time.Instant

/**
 * 장바구니식 주문. 여러 [OrderLine] 을 담는다.
 *
 * 결제·배송 상태를 두지 않는다. `PLACED` → `CANCELLED` 뿐이다(ADR 0016).
 */
class Order private constructor(
    val id: Long?,
    val buyerId: Long,
    val lines: List<OrderLine>,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 파생값이다. 별도 컬럼으로 저장하지 않는다 - 라인 합과 어긋날 수가 없다 */
    val totalAmount: Money
        get() = lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }

    /**
     * 취소.
     *
     * 여기서 하는 검사는 [OrderRepository.cancelIfPlaced] 로 원자 갱신하기 전의 사전
     * 확인이다. 동시에 들어온 취소 요청 두 개가 이 검사를 둘 다 통과할 수 있으므로, 실제
     * 전이 성공 여부는 그 원자 갱신의 반환값으로 다시 확인해야 한다(ADR 0016).
     */
    fun cancel(now: Instant): Order {
        if (status != OrderStatus.PLACED) {
            throw OrderNotCancellableException()
        }
        return Order(id, buyerId, lines, OrderStatus.CANCELLED, createdAt, now)
    }

    override fun toString(): String = "Order(id=$id, buyerId=$buyerId, status=$status)"

    companion object {
        /** 신규 주문. 라인이 비었거나 같은 상품을 중복해서 담으면 거절한다 */
        fun place(
            buyerId: Long,
            lines: List<OrderLine>,
            now: Instant,
        ): Order {
            if (lines.isEmpty()) {
                throw InvalidValueException("items", "최소 1개 이상이어야 합니다.")
            }
            if (lines.map { it.productId }.distinct().size != lines.size) {
                throw InvalidValueException("items", "동일한 상품을 중복해서 담을 수 없습니다.")
            }
            return Order(
                id = null,
                buyerId = buyerId,
                lines = lines,
                status = OrderStatus.PLACED,
                createdAt = now,
                updatedAt = now,
            )
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(
            id: Long,
            buyerId: Long,
            lines: List<OrderLine>,
            status: OrderStatus,
            createdAt: Instant,
            updatedAt: Instant,
        ): Order = Order(id, buyerId, lines, status, createdAt, updatedAt)
    }
}
