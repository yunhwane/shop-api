package com.example.shopapi.core.enums

/**
 * 결제 시도 하나의 상태. `READY` → `DONE`/`FAILED`, `DONE` → `CANCELLED` 뿐이다.
 *
 * 같은 주문의 서로 다른 결제 시도가 동시에 Toss 승인을 받는 경합은 이 상태가 아니라
 * `OrderRepository` 의 선점(`claimPaymentIfPlaced`)이 막는다(ADR 0019) - `Payment` 자신의
 * 상태 전이는 그 선점 여부와 무관하게 그대로다.
 *
 * 웹훅이 없어 비동기로 상태가 바뀌는 경로가 없다(ADR 0017). 그 경로가 생기면
 * 가상계좌 입금 대기 같은 상태가 여기에 늘어난다. `CANCELLED` 는 전액 취소만
 * 표현한다 - 부분 취소가 생기면 별도 상태나 잔액 필드가 필요하다(ADR 0018).
 */
enum class PaymentStatus {
    READY,
    DONE,
    FAILED,
    CANCELLED,
}
