package com.example.shopapi.api.shipment

import com.example.shopapi.api.auth.AuthenticatedUser
import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.shipment.application.ShipmentQueryService
import com.example.shopapi.api.shipment.dto.ShipmentResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 배송 조회. 읽기만 연다 - 상태를 바꾸는 쪽은 컨트롤러가 없다(ADR 0020).
 *
 * 경로를 주문 아래 두는 이유는 배송 식별자를 밖으로 내보내지 않기 위해서다. 구매자가
 * 아는 것은 자기 주문 번호뿐이고, 배송은 그 주문에 하나뿐이다.
 */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/shipment")
class ShipmentController(
    private val shipmentQueryService: ShipmentQueryService,
) {
    @GetMapping
    fun detail(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<ShipmentResponse>> =
        ResponseEntity.ok(ApiResponse.of(ShipmentResponse.from(shipmentQueryService.findMine(principal.id, orderId))))
}
