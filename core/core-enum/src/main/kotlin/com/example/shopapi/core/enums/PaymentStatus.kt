package com.example.shopapi.core.enums

/**
 * 결제 시도 하나의 상태. `READY` → `DONE`/`FAILED`, `DONE` → `CANCELLED` 뿐이다.
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
