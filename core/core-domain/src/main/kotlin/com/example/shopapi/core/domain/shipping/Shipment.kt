package com.example.shopapi.core.domain.shipping

import com.example.shopapi.core.enums.ShipmentStatus
import java.time.Instant

/**
 * 배송 하나. `Order` 객체가 아니라 [orderId] 만 참조한다 - `Payment` 와 같은 이유로,
 * 배송도 주문과 독립적인 생명주기를 갖는 애그리게이트다(ADR 0017, ADR 0020).
 *
 * 주문 하나에 배송 하나다. 부분배송은 이번 범위 밖이라 [orderId] 가 사실상 유일 키다.
 *
 * 상태 전이 검사에 `DomainException` 대신 [check] 를 쓴다. 이 애그리게이트를 바꾸는
 * 경로는 컨트롤러가 없는 내부 유스케이스뿐이라, HTTP 응답 계약(ADR 0006)을 씌울 대상이
 * 아니다(ADR 0020).
 */
class Shipment private constructor(
    val id: Long?,
    val orderId: Long,
    val address: ShippingAddress,
    val status: ShipmentStatus,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 발송. [ShipmentStatus.PREPARING] 이 아니면 거부한다 */
    fun startShipping(now: Instant): Shipment {
        check(status == ShipmentStatus.PREPARING) { "준비 중인 배송만 발송할 수 있다. status=$status" }
        return Shipment(id, orderId, address, ShipmentStatus.SHIPPING, now, deliveredAt, createdAt, now)
    }

    /** 배송 완료. [ShipmentStatus.SHIPPING] 이 아니면 거부한다 */
    fun markDelivered(now: Instant): Shipment {
        check(status == ShipmentStatus.SHIPPING) { "배송 중인 건만 배송 완료할 수 있다. status=$status" }
        return Shipment(id, orderId, address, ShipmentStatus.DELIVERED, shippedAt, now, createdAt, now)
    }

    override fun toString(): String = "Shipment(id=$id, orderId=$orderId, status=$status)"

    companion object {
        /**
         * 새 배송. [address] 는 호출자가 그 시점의 `Order.shippingAddress` 를 스냅샷해서 넘긴다 -
         * `Payment.ready` 가 `Order.totalAmount` 를 넘겨받는 것과 같다(ADR 0020).
         */
        fun prepare(
            orderId: Long,
            address: ShippingAddress,
            now: Instant,
        ): Shipment =
            Shipment(
                id = null,
                orderId = orderId,
                address = address,
                status = ShipmentStatus.PREPARING,
                shippedAt = null,
                deliveredAt = null,
                createdAt = now,
                updatedAt = now,
            )

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(
            id: Long,
            orderId: Long,
            address: ShippingAddress,
            status: ShipmentStatus,
            shippedAt: Instant?,
            deliveredAt: Instant?,
            createdAt: Instant,
            updatedAt: Instant,
        ): Shipment = Shipment(id, orderId, address, status, shippedAt, deliveredAt, createdAt, updatedAt)
    }
}
