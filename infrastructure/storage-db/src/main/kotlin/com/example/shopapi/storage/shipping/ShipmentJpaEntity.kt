package com.example.shopapi.storage.shipping

import com.example.shopapi.core.domain.shipping.Shipment
import com.example.shopapi.core.enums.ShipmentStatus
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 배송의 영속성 모델.
 *
 * `order_id` 가 유니크다 - 주문 하나에 배송 하나라는 규칙(ADR 0020)을 DB 제약으로도
 * 세운다. 결제 확정이 두 번 일어나 배송이 두 번 만들어지려 하면 여기서 걸린다.
 */
@Entity
@Table(
    name = "shipments",
    uniqueConstraints = [UniqueConstraint(name = "uk_shipments_order_id", columnNames = ["order_id"])],
)
class ShipmentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "order_id", nullable = false)
    var orderId: Long,
    @Embedded
    var address: ShippingAddressEmbeddable,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ShipmentStatus,
    @Column(name = "shipped_at")
    var shippedAt: Instant?,
    @Column(name = "delivered_at")
    var deliveredAt: Instant?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    fun toDomain(): Shipment =
        Shipment.reconstitute(
            id = requireNotNull(id) { "저장된 배송이어야 한다" },
            orderId = orderId,
            address = address.toDomain(),
            status = status,
            shippedAt = shippedAt,
            deliveredAt = deliveredAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun from(shipment: Shipment): ShipmentJpaEntity =
            ShipmentJpaEntity(
                id = shipment.id,
                orderId = shipment.orderId,
                address = ShippingAddressEmbeddable.from(shipment.address),
                status = shipment.status,
                shippedAt = shipment.shippedAt,
                deliveredAt = shipment.deliveredAt,
                createdAt = shipment.createdAt,
                updatedAt = shipment.updatedAt,
            )
    }
}
