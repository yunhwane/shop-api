package com.example.shopapi.core.enums

/**
 * 배송 하나의 상태. `PREPARING` → `SHIPPING` → `DELIVERED` 뿐이다(ADR 0020).
 *
 * 취소 상태를 두지 않는다 - 결제완료 주문이 환불될 때(ADR 0018) 이미 나간 배송을 어떻게
 * 다룰지는 반품 회수 절차가 필요한 문제라, 상태 하나로 표현하지 않고 미뤄 뒀다.
 *
 * 실제 택배사를 부르지 않으므로 이 값은 전부 우리 쪽 판단이다. 연동이 생기면 택배사가
 * 알려주는 상태(집화, 간선상차 같은)를 여기에 어떻게 접을지 다시 정한다.
 */
enum class ShipmentStatus {
    PREPARING,
    SHIPPING,
    DELIVERED,
}
