package com.example.shopapi.core.enums

/**
 * 주문 상태. `PLACED` → `CANCELLED` 만 있다.
 *
 * 결제·배송 상태를 아직 두지 않는다(ADR 0016). 그 도메인이 생기면 여기에 상태가 늘어난다.
 */
enum class OrderStatus {
    PLACED,
    CANCELLED,
}
