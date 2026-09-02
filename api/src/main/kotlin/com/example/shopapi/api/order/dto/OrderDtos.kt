package com.example.shopapi.api.order.dto

import com.example.shopapi.api.order.application.PlaceOrderCommand
import com.example.shopapi.api.order.application.PlaceOrderItemCommand
import com.example.shopapi.api.order.application.ShippingAddressCommand
import com.example.shopapi.api.order.support.OrderCursors
import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderLine
import com.example.shopapi.core.domain.shipping.ShippingAddress
import com.example.shopapi.core.enums.OrderStatus
import java.time.Instant

data class PlaceOrderRequest(
    val items: List<PlaceOrderItemRequest>,
    val shippingAddress: ShippingAddressRequest,
) {
    fun toCommand(): PlaceOrderCommand =
        PlaceOrderCommand(
            items = items.map { PlaceOrderItemCommand(it.productId, it.quantity) },
            shippingAddress = shippingAddress.toCommand(),
        )
}

data class PlaceOrderItemRequest(
    val productId: Long,
    val quantity: Int,
)

data class ShippingAddressRequest(
    val recipientName: String,
    val phone: String,
    val postalCode: String,
    val addressLine1: String,
    val addressLine2: String?,
) {
    fun toCommand(): ShippingAddressCommand =
        ShippingAddressCommand(
            recipientName = recipientName,
            phone = phone,
            postalCode = postalCode,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
        )
}

data class ShippingAddressResponse(
    val recipientName: String,
    val phone: String,
    val postalCode: String,
    val addressLine1: String,
    val addressLine2: String?,
) {
    companion object {
        fun from(address: ShippingAddress): ShippingAddressResponse =
            ShippingAddressResponse(
                recipientName = address.recipientName.value,
                phone = address.phone.value,
                postalCode = address.postalCode.value,
                addressLine1 = address.addressLine1.value,
                addressLine2 = address.addressLine2?.value,
            )
    }
}

data class OrderLineResponse(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
) {
    companion object {
        fun from(line: OrderLine): OrderLineResponse =
            OrderLineResponse(
                productId = line.productId,
                productName = line.productName.value,
                unitPrice = line.unitPrice.amount,
                quantity = line.quantity.value,
                lineTotal = line.lineTotal.amount,
            )
    }
}

data class OrderResponse(
    val id: Long,
    val status: OrderStatus,
    val totalAmount: Long,
    val lines: List<OrderLineResponse>,
    val shippingAddress: ShippingAddressResponse,
    val createdAt: Instant,
) {
    companion object {
        fun from(order: Order): OrderResponse =
            OrderResponse(
                id = requireNotNull(order.id) { "저장된 주문이어야 한다" },
                status = order.status,
                totalAmount = order.totalAmount.amount,
                lines = order.lines.map { OrderLineResponse.from(it) },
                shippingAddress = ShippingAddressResponse.from(order.shippingAddress),
                createdAt = order.createdAt,
            )
    }
}

data class OrderListResponse(
    val items: List<OrderResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        /** [fetched] 는 요청한 [size] 보다 한 개 더 읽어온 결과다(ADR 0015 와 같은 방식) */
        fun from(
            fetched: List<Order>,
            size: Int,
        ): OrderListResponse {
            val items = fetched.take(size)
            val hasNext = fetched.size > size
            val nextCursor = if (hasNext) OrderCursors.encode(requireNotNull(items.last().id)) else null
            return OrderListResponse(
                items = items.map { OrderResponse.from(it) },
                nextCursor = nextCursor,
                hasNext = hasNext,
            )
        }
    }
}
