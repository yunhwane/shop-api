package com.example.shopapi.api.order.application

/**
 * 주문 생성 입력.
 *
 * 값 객체가 아니라 원시 타입을 받는다. 형식 검증은 유스케이스가 각 항목으로 상품을
 * 조회하고 값 객체를 만드는 과정에서 일어난다 - `RegisterProductCommand` 와 같은 방식이다.
 */
data class PlaceOrderCommand(
    val items: List<PlaceOrderItemCommand>,
)

data class PlaceOrderItemCommand(
    val productId: Long,
    val quantity: Int,
)
