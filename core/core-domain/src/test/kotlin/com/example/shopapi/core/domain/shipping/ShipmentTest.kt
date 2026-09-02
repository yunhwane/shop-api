package com.example.shopapi.core.domain.shipping

import com.example.shopapi.core.enums.ShipmentStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ShipmentTest {
    private val now = Instant.parse("2026-09-02T00:00:00Z")
    private val later = now.plusSeconds(60)

    private val address =
        ShippingAddress(
            recipientName = RecipientName.of("전윤환"),
            phone = PhoneNumber.of("010-1234-5678"),
            postalCode = PostalCode.of("04524"),
            addressLine1 = AddressLine1.of("서울 중구 세종대로 110"),
            addressLine2 = AddressLine2.of("5층"),
        )

    private fun prepared(): Shipment = Shipment.prepare(orderId = 1L, address = address, now = now)

    @Test
    fun `준비하면 PREPARING 으로 시작하고 시각이 비어 있다`() {
        val shipment = prepared()

        assertEquals(ShipmentStatus.PREPARING, shipment.status)
        assertEquals(address, shipment.address)
        assertNull(shipment.shippedAt)
        assertNull(shipment.deliveredAt)
    }

    @Test
    fun `발송하면 SHIPPING 이 되고 발송 시각을 담는다`() {
        val shipped = prepared().startShipping(later)

        assertEquals(ShipmentStatus.SHIPPING, shipped.status)
        assertEquals(later, shipped.shippedAt)
        assertNull(shipped.deliveredAt)
    }

    @Test
    fun `이미 발송한 배송은 다시 발송할 수 없다`() {
        val shipped = prepared().startShipping(later)

        assertFailsWith<IllegalStateException> { shipped.startShipping(later.plusSeconds(60)) }
    }

    @Test
    fun `배송 완료하면 DELIVERED 가 되고 발송 시각은 그대로 남는다`() {
        val delivered = prepared().startShipping(later).markDelivered(later.plusSeconds(60))

        assertEquals(ShipmentStatus.DELIVERED, delivered.status)
        assertEquals(later, delivered.shippedAt)
        assertEquals(later.plusSeconds(60), delivered.deliveredAt)
    }

    /** 발송하지 않은 배송이 배송완료가 되면 추적이 거짓말을 한다 */
    @Test
    fun `준비 중인 배송은 곧바로 완료할 수 없다`() {
        assertFailsWith<IllegalStateException> { prepared().markDelivered(later) }
    }

    @Test
    fun `이미 완료된 배송은 다시 완료할 수 없다`() {
        val delivered = prepared().startShipping(later).markDelivered(later.plusSeconds(60))

        assertFailsWith<IllegalStateException> { delivered.markDelivered(later.plusSeconds(120)) }
    }
}
