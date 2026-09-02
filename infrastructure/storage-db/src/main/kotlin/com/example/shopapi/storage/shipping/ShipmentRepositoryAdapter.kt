package com.example.shopapi.storage.shipping

import com.example.shopapi.core.domain.port.ShipmentRepository
import com.example.shopapi.core.domain.shipping.Shipment
import com.example.shopapi.core.enums.ShipmentStatus
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
internal class ShipmentRepositoryAdapter(
    private val jpaRepository: ShipmentJpaRepository,
) : ShipmentRepository {
    /** [Shipment.id] 가 이미 있는 값을 받으면 실패한다 - 상태 전이는 [startShippingIfPreparing] 을 쓴다 */
    override fun save(shipment: Shipment): Shipment {
        check(shipment.id == null) {
            "이미 저장된 배송은 save 로 갱신할 수 없다. 상태 전이는 startShippingIfPreparing/markDeliveredIfShipping 을 쓴다"
        }
        return jpaRepository.save(ShipmentJpaEntity.from(shipment)).toDomain()
    }

    override fun findById(id: Long): Shipment? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByOrderId(orderId: Long): Shipment? = jpaRepository.findByOrderId(orderId)?.toDomain()

    override fun startShippingIfPreparing(
        id: Long,
        now: Instant,
    ): Boolean = jpaRepository.startShippingIfPreparing(id, ShipmentStatus.PREPARING, ShipmentStatus.SHIPPING, now) == 1

    override fun markDeliveredIfShipping(
        id: Long,
        now: Instant,
    ): Boolean = jpaRepository.markDeliveredIfShipping(id, ShipmentStatus.SHIPPING, ShipmentStatus.DELIVERED, now) == 1
}
