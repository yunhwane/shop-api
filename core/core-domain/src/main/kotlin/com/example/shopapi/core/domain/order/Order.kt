package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.common.reconstituting
import com.example.shopapi.core.domain.shipping.ShippingAddress
import com.example.shopapi.core.enums.OrderStatus
import java.time.Instant

/**
 * 장바구니식 주문. 여러 [OrderLine] 을 담는다.
 *
 * `PLACED` 에서 `PAID`(결제 확정, ADR 0017) 또는 `CANCELLED`(ADR 0016) 로 전이하고,
 * `PAID` 에서도 `CANCELLED`(환불, ADR 0018) 로 전이한다. 배송 상태는 아직 두지 않는다.
 */
class Order private constructor(
    val id: Long?,
    val buyerId: Long,
    val lines: List<OrderLine>,
    val shippingAddress: ShippingAddress,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * 파생값이다. 별도 컬럼으로 저장하지 않는다 - 라인 합과 어긋날 수가 없다.
     *
     * `Money` 의 상한을 넘으면 여기서 던진다. [place] 가 재고를 건드리기 전에 이 값을
     * 강제로 한 번 읽는 이유는, 지연 계산인 이 프로퍼티를 응답을 만들 때야 처음 읽으면
     * 재고 차감과 저장이 이미 끝난 뒤에 실패하기 때문이다.
     */
    val totalAmount: Money
        get() = lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }

    /**
     * 취소.
     *
     * 여기서 하는 검사는 [OrderRepository.cancelIfPlaced]/[OrderRepository.cancelIfPaid] 로
     * 원자 갱신하기 전의 사전 확인이다. 동시에 들어온 취소 요청 두 개가 이 검사를 둘 다
     * 통과할 수 있으므로, 실제 전이 성공 여부는 그 원자 갱신의 반환값으로 다시 확인해야
     * 한다(ADR 0016).
     *
     * `PLACED`, `PAID` 둘 다 통과한다 - 결제 완료 주문의 취소는 PG 환불을 동반할 뿐
     * 주문 상태로는 같은 `CANCELLED` 다(ADR 0018). `CANCELLED` 자신은 걸러진다.
     */
    fun cancel(now: Instant): Order {
        if (status != OrderStatus.PLACED && status != OrderStatus.PAID) {
            throw OrderNotCancellableException()
        }
        return Order(id, buyerId, lines, shippingAddress, OrderStatus.CANCELLED, createdAt, now)
    }

    /**
     * 결제 시작이 가능한 상태인지 미리 확인한다. Toss 를 부르기 전, 결제 시도를
     * 만들기 전에 쓴다.
     */
    fun ensurePayable() {
        if (status != OrderStatus.PLACED) {
            throw OrderNotPayableException()
        }
    }

    /**
     * 결제 확정 반영. [OrderRepository.markPaidIfPlaced] 로 원자 갱신하기 전의 사전
     * 확인이라는 점은 [cancel] 과 같다(ADR 0017).
     */
    fun pay(now: Instant): Order {
        ensurePayable()
        return Order(id, buyerId, lines, shippingAddress, OrderStatus.PAID, createdAt, now)
    }

    override fun toString(): String = "Order(id=$id, buyerId=$buyerId, status=$status)"

    companion object {
        /**
         * 신규 주문. 라인이 비었거나 같은 상품을 중복해서 담으면 거절한다.
         *
         * [shippingAddress] 는 주문할 때마다 새로 받는다 - 재사용하는 주소록을 두지
         * 않기로 했고, 주문 뒤에는 바꿀 수 없다(ADR 0020).
         */
        fun place(
            buyerId: Long,
            lines: List<OrderLine>,
            shippingAddress: ShippingAddress,
            now: Instant,
        ): Order {
            if (lines.isEmpty()) {
                throw InvalidValueException("items", "최소 1개 이상이어야 합니다.")
            }
            if (lines.map { it.productId }.distinct().size != lines.size) {
                throw InvalidValueException("items", "동일한 상품을 중복해서 담을 수 없습니다.")
            }
            val order =
                Order(
                    id = null,
                    buyerId = buyerId,
                    lines = lines,
                    shippingAddress = shippingAddress,
                    status = OrderStatus.PLACED,
                    createdAt = now,
                    updatedAt = now,
                )
            order.totalAmount
            return order
        }

        /**
         * 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다.
         *
         * [totalAmount] 를 여기서도 강제로 읽어, 저장된 값이 `Money` 상한을 어기면
         * [place] 와 달리 [com.example.shopapi.core.domain.common.CorruptedDataException]
         * 으로 답한다 - 서버 데이터 문제를 클라이언트 입력 탓으로 돌리지 않는다(ADR 0007).
         */
        fun reconstitute(
            id: Long,
            buyerId: Long,
            lines: List<OrderLine>,
            shippingAddress: ShippingAddress,
            status: OrderStatus,
            createdAt: Instant,
            updatedAt: Instant,
        ): Order {
            val order = Order(id, buyerId, lines, shippingAddress, status, createdAt, updatedAt)
            reconstituting("totalAmount") { order.totalAmount }
            return order
        }
    }
}
