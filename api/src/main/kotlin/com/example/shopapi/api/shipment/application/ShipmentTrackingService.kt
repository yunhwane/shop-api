package com.example.shopapi.api.shipment.application

import com.example.shopapi.core.domain.port.ShipmentRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.shipping.Shipment
import com.example.shopapi.core.domain.shipping.ShipmentNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 배송 상태를 옮긴다.
 *
 * **컨트롤러가 없다.** `ProductRegistrationService` 와 같은 이유다 - 권한 모델이 없는
 * 상태에서 쓰기 엔드포인트를 열면, 인증만으로는 인가가 되지 않아 로그인한 아무 회원이나
 * 남의 배송을 배송완료로 바꿀 수 있다. 도메인 규칙은 지금 확정하고, "누가 할 수 있는가"
 * 가 정해질 때 컨트롤러만 얹는다(ADR 0020).
 *
 * 지금 이 유스케이스를 부르는 것은 테스트뿐이다.
 *
 * 사전 검사([Shipment.startShipping])와 조건부 원자 갱신을 둘 다 거치는 이유는
 * `CancelOrderService` 와 같다 - 앞은 정확한 실패 사유를 위해서고, 뒤는 실제 전이에
 * 성공한 쪽만 다음 단계로 가게 하기 위해서다.
 */
@Service
class ShipmentTrackingService(
    private val shipments: ShipmentRepository,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun startShipping(orderId: Long): Shipment =
        advance(orderId) { shipment, now ->
            val shipped = shipment.startShipping(now)
            check(shipments.startShippingIfPreparing(requireNotNull(shipment.id), now)) {
                "배송을 발송으로 옮기지 못했다. orderId=$orderId"
            }
            shipped
        }

    @Transactional
    fun markDelivered(orderId: Long): Shipment =
        advance(orderId) { shipment, now ->
            val delivered = shipment.markDelivered(now)
            check(shipments.markDeliveredIfShipping(requireNotNull(shipment.id), now)) {
                "배송을 완료로 옮기지 못했다. orderId=$orderId"
            }
            delivered
        }

    private fun advance(
        orderId: Long,
        change: (Shipment, java.time.Instant) -> Shipment,
    ): Shipment {
        val shipment = shipments.findByOrderId(orderId) ?: throw ShipmentNotFoundException()
        return change(shipment, timeProvider.now())
    }
}
