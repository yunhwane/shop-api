package com.example.shopapi.api.payment

import com.example.shopapi.api.auth.AuthenticatedUser
import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.order.dto.OrderResponse
import com.example.shopapi.api.payment.application.ConfirmPaymentService
import com.example.shopapi.api.payment.application.ReadyPaymentService
import com.example.shopapi.api.payment.dto.ConfirmPaymentRequest
import com.example.shopapi.api.payment.dto.PaymentReadyResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 본인 소유 주문의 결제만 다룬다. 주문을 찾지 못하는 경우와 남의 주문인 경우를
 * [com.example.shopapi.core.domain.order.OrderNotFoundException] 하나로 통일한다(ADR 0016).
 */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
class PaymentController(
    private val readyPaymentService: ReadyPaymentService,
    private val confirmPaymentService: ConfirmPaymentService,
) {
    /** Toss 결제창을 열 때 프론트가 쓸 `tossOrderId` 와 금액을 발급한다 */
    @PostMapping
    fun ready(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<PaymentReadyResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(PaymentReadyResponse.from(readyPaymentService.ready(principal.id, orderId))))

    /** 결제창에서 돌아온 값으로 Toss 승인을 확정하고, 성공하면 주문을 PAID 로 옮긴다 */
    @PostMapping("/confirm")
    fun confirm(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable orderId: Long,
        @RequestBody request: ConfirmPaymentRequest,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = confirmPaymentService.confirm(principal.id, orderId, request.toCommand())
        return ResponseEntity.ok(ApiResponse.of(OrderResponse.from(order)))
    }
}
