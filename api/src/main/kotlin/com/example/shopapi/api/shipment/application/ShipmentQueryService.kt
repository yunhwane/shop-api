package com.example.shopapi.api.shipment.application

import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.ShipmentRepository
import com.example.shopapi.core.domain.shipping.Shipment
import com.example.shopapi.core.domain.shipping.ShipmentNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증된 회원 본인 주문의 배송 조회.
 *
 * 상태를 바꾸는 쪽([ShipmentTrackingService])과 달리 이쪽은 HTTP 로 열려 있다 - 자기
 * 배송이 지금 어디쯤인지 보는 것은 권한 모델을 기다릴 이유가 없다(ADR 0020).
 *
 * 소유권 확인을 위해 배송이 아니라 주문부터 읽는다. `Shipment` 은 `orderId` 만 알고
 * `buyerId` 를 모르기 때문이다 - 남의 주문이면 `ORDER_NOT_FOUND` 로 존재를 감춘다(ADR 0016).
 */
@Service
class ShipmentQueryService(
    private val orders: OrderRepository,
    private val shipments: ShipmentRepository,
) {
    @Transactional(readOnly = true)
    fun findMine(
        buyerId: Long,
        orderId: Long,
    ): Shipment {
        val order = orders.findById(orderId) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) {
            throw OrderNotFoundException()
        }
        return shipments.findByOrderId(orderId) ?: throw ShipmentNotFoundException()
    }
}
