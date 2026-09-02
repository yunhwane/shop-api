package com.example.shopapi.api.order

import com.example.shopapi.api.auth.AuthenticatedUser
import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.order.application.CancelOrderService
import com.example.shopapi.api.order.application.OrderQueryService
import com.example.shopapi.api.order.application.PlaceOrderService
import com.example.shopapi.api.order.dto.OrderListResponse
import com.example.shopapi.api.order.dto.OrderResponse
import com.example.shopapi.api.order.dto.PlaceOrderRequest
import com.example.shopapi.api.order.support.OrderCursors
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 인증된 회원 본인의 주문만 다룬다.
 *
 * `SecurityConfig` 에 별도 규칙을 추가하지 않는다. 기본값 `anyRequest().authenticated()`
 * 가 이미 이 경로를 막는다.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val placeOrderService: PlaceOrderService,
    private val orderQueryService: OrderQueryService,
    private val cancelOrderService: CancelOrderService,
) {
    @PostMapping
    fun place(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: PlaceOrderRequest,
    ): ResponseEntity<ApiResponse<OrderResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(OrderResponse.from(placeOrderService.place(principal.id, request.toCommand()))))

    @GetMapping("/{id}")
    fun detail(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<OrderResponse>> =
        ResponseEntity.ok(ApiResponse.of(OrderResponse.from(orderQueryService.findMine(principal.id, id))))

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "${OrderQueryService.DEFAULT_SIZE}") size: Int,
    ): ResponseEntity<ApiResponse<OrderListResponse>> {
        val fetched = orderQueryService.listMine(principal.id, cursor?.let { OrderCursors.decode(it) }, size)
        return ResponseEntity.ok(ApiResponse.of(OrderListResponse.from(fetched, size)))
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<OrderResponse>> =
        ResponseEntity.ok(ApiResponse.of(OrderResponse.from(cancelOrderService.cancel(principal.id, id))))
}
