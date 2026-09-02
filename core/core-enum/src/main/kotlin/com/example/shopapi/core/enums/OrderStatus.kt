package com.example.shopapi.core.enums

/**
 * 주문 상태. `PLACED` → `PAID`/`CANCELLED`, `PAID` → `CANCELLED` 뿐이다(ADR 0017,
 * ADR 0018). `CANCELLED` 하나가 "결제 전 취소"와 "결제 후 환불"을 둘 다 표현한다 -
 * 돈이 실제로 돌아왔는지는 `Payment` 쪽 상태로 구분한다.
 *
 * 배송 상태는 아직 두지 않는다(ADR 0016). 그 도메인이 생기면 여기에 상태가 늘어난다.
 */
enum class OrderStatus {
    PLACED,
    PAID,
    CANCELLED,
}
