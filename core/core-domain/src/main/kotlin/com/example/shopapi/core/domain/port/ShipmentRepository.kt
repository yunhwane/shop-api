package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.shipping.Shipment
import java.time.Instant

/**
 * 배송 저장소.
 *
 * 상태 전이를 조건부 원자 갱신으로만 하는 이유는 `OrderRepository.cancelIfPlaced` 와
 * 같다(ADR 0014, ADR 0016). 배송은 지금 동시에 두 번 요청될 경로가 없지만 - 상태를
 * 바꾸는 유스케이스에 컨트롤러가 없다(ADR 0020) - 그 경로가 생겼을 때 이 저장소만
 * 그대로 쓰면 되도록 같은 모양으로 맞춰 둔다.
 */
interface ShipmentRepository {
    /**
     * **새 배송만 저장한다.** [Shipment.id] 가 이미 있는 값을 받으면 구현은 실패해야 한다.
     *
     * 상태 전이는 이 메서드를 거치지 않는다 - [startShippingIfPreparing] 을 쓴다.
     */
    fun save(shipment: Shipment): Shipment

    fun findById(id: Long): Shipment?

    /** 주문 하나에 배송 하나다(ADR 0020) - 여러 건이 나올 일이 없다 */
    fun findByOrderId(orderId: Long): Shipment?

    /** `PREPARING` 일 때만 `SHIPPING` 으로 전이하며 [now] 를 발송 시각으로 함께 쓰고, 전이했는지 알려준다 */
    fun startShippingIfPreparing(
        id: Long,
        now: Instant,
    ): Boolean

    /** `SHIPPING` 일 때만 `DELIVERED` 로 전이하며 [now] 를 완료 시각으로 함께 쓰고, 전이했는지 알려준다 */
    fun markDeliveredIfShipping(
        id: Long,
        now: Instant,
    ): Boolean
}
