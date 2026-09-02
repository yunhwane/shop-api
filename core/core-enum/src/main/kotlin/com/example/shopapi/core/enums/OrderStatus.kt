package com.example.shopapi.core.enums

/**
 * 주문 상태. `PLACED` → `PAID` 또는 `PLACED` → `CANCELLED` 뿐이다. `PAID` 에서는
 * 전이가 없다(ADR 0017) - 결제 완료 주문의 취소는 이 상태 하나로 막힌다.
 *
 * 배송 상태는 아직 두지 않는다(ADR 0016). 그 도메인이 생기면 여기에 상태가 늘어난다.
 */
enum class OrderStatus {
    PLACED,
    PAID,
    CANCELLED,
}
