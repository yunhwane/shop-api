package com.example.shopapi.api.shipment.dto

import com.example.shopapi.api.order.dto.ShippingAddressResponse
import com.example.shopapi.core.domain.shipping.Shipment
import com.example.shopapi.core.enums.ShipmentStatus
import java.time.Instant

data class ShipmentResponse(
    val orderId: Long,
    val status: ShipmentStatus,
    val shippingAddress: ShippingAddressResponse,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(shipment: Shipment): ShipmentResponse =
            ShipmentResponse(
                orderId = shipment.orderId,
                status = shipment.status,
                shippingAddress = ShippingAddressResponse.from(shipment.address),
                shippedAt = shipment.shippedAt,
                deliveredAt = shipment.deliveredAt,
                createdAt = shipment.createdAt,
            )
    }
}
