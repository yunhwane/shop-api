# 0019. 같은 주문의 동시 확정은 주문 행 하나를 선점해서 막는다

- 상태: 수락됨
- 날짜: 2026-09-02
- 관련: [0014](0014-stock-and-oversell.md), [0016](0016-order-domain-scope.md),
  [0017](0017-toss-payment-integration.md), [0018](0018-payment-refund-via-order-cancel.md)

## 맥락

[0017](0017-toss-payment-integration.md)의 근거 절이 방치하기로 한 경합은 사실 두 가지가
섞여 있었다. 하나는 "취소가 먼저 `PLACED`를 `CANCELLED`로 옮기는 사이 confirm이 Toss
승인을 받아 버리는" 경합(주문 하나에 결제 시도 하나)이고, 다른 하나는 **같은 주문에 걸린
서로 다른 결제 시도 두 개가 동시에 confirm되는 경합**이다. `ready`는 실패한 시도의
재시도를 위해 같은 주문에 여러 `READY` 행을 허용하므로, 그 중 둘이 동시에 confirm
요청을 받으면 `markDoneIfReady`가 각자 독립적으로 성공해 **`DONE` 결제가 한 주문에
두 개** 남는다 - 이때 Toss 는 각 시도를 서로 다른 `paymentKey`로 실제로 승인해 버렸으므로,
DB 정합성 문제가 아니라 **카드가 실제로 두 번 결제된다.**

이번 ADR은 후자만 다룬다. 전자(취소·confirm 경합)는 여전히 범위 밖이다 - 그 경합은
`PLACED`에서만 일어나고 재고 복원과 얽혀 있어 성격이 다르다.

## 결정

**주문 행 하나에 "지금 이 주문의 확정을 누가 진행 중인가"를 선점시킨다.** `orders`
테이블에 `active_payment_id`(nullable) 컬럼을 추가한다 - `Order` 도메인 모델에는
이 필드를 두지 않는다. 순수하게 동시성 제어용이라 `cancelIfPlaced`/`markPaidIfPlaced`가
`Order.status`라는 실제 도메인 값을 원자적으로 바꾸는 것과 달리, `active_payment_id`는
어떤 도메인 상태도 표현하지 않는다.

```sql
update orders set active_payment_id = :paymentId, updated_at = :now
where id = :orderId and status = 'PLACED' and active_payment_id is null
```

`OrderRepository`에 `claimPaymentIfPlaced(id, paymentId, now): Boolean`을 추가한다.
Toss 를 부르기 전에 이 선점을 먼저 얻어야 하고, 실패하면 Toss 를 아예 부르지 않고
거절한다. 대응하는 `releaseClaimedPayment(id, paymentId, now): Boolean`은 Toss 호출이
실패했을 때 선점을 풀어준다 - 풀어주지 않으면 그 주문의 모든 후속 결제 시도가 영원히
막힌다.

`ConfirmPaymentService`의 순서에 한 단계가 늘어난다. 기존의 금액 대조·`ensurePayable`
사전 검사 다음, Toss 를 부르기 직전에 `claimPaymentIfPlaced`를 부른다. Toss 가 실패하면
`markFailedIfReady`와 함께 `releaseClaimedPayment`도 부른다. `Payment` 도메인 모델과
`PaymentStatus`는 손대지 않는다 - 이 메커니즘은 전적으로 `Order` 쪽 컬럼 하나로 끝난다.

## 근거

**처음 시도(결제 시도 쪽에 `CONFIRMING` 상태와 형제 행 서브쿼리)가 실패한 이유**

첫 설계는 `PaymentStatus`에 `CONFIRMING`을 끼워 넣고, `READY → CONFIRMING` 전이 자체에
"같은 주문의 다른 결제 시도가 `CONFIRMING`/`DONE`이 아닐 때만"이라는 `NOT EXISTS`
서브쿼리 조건을 붙였다. 코드로는 조건부 원자 갱신처럼 보였지만, 동시성 테스트
(`ConfirmPaymentConcurrencyTest`, 결제 시도 10개를 같은 주문에 동시에 confirm)로 실제
검증하자 10번 중 매번은 아니어도 재현 가능하게 **둘 이상이 성공**했다.

원인은 두 요청의 `UPDATE`가 각자 **다른 행**(결제 시도 A, B)을 잠근다는 데 있다. A 의
갱신과 B 의 갱신은 서로 다른 행을 대상으로 하므로 행 잠금이 충돌하지 않고, `READ_COMMITTED`
격리 수준에서 B 의 서브쿼리는 A 가 아직 커밋하지 않은 상태를 보지 못한다 - 그래서 B 는
"형제가 없다"고 (틀리게) 판단하고 통과해 버린다. "조건부 원자 갱신" 이 이 코드베이스
전체에서 항상 안전했던 이유는 매번 **같은 행**(자기 자신의 기본키)을 두고 `UPDATE ...
WHERE id = :id AND status = :current` 형태로 겨뤘기 때문이다 - `cancelIfPlaced`,
`markPaidIfPlaced`, `decreaseStockIfEnough` 모두 그렇다. 서로 다른 두 행을 서브쿼리로
엮은 것은 이번이 처음이었고, 그 지점에서 이 코드베이스가 지금까지 의존해 온 전제
(같은 행 CAS)가 깨졌다.

**왜 주문 행을 선점 대상으로 고르는가**

같은 주문에 걸린 confirm 요청 두 개가 항상 공유하는 단 하나의 행은 그 주문 자신이다.
`orders.active_payment_id`를 두고 겨루면 두 요청은 항상 **같은 행**을 잠그므로, 표준
행 잠금이 정확히 하나만 통과시킨다 - 이 코드베이스의 다른 모든 조건부 원자 갱신과
같은 보장이다. `Payment` 쪽에 무언가를 남기는 접근(형제 행 서브쿼리든 다른 방식이든)은
근본적으로 "여러 행 중 하나만" 판정이라 이 보장을 줄 수 없다.

**왜 DB 락을 Toss 호출 동안 들고 있지 않는가**

대안으로 confirm 트랜잭션이 시작할 때 주문 행에 `SELECT ... FOR UPDATE`를 걸어 동시
confirm을 통째로 직렬화하는 방법도 있다. 하지만 Toss 응답 대기(`readTimeout`, 최대
5초)는 이미 "요청 스레드를 붙잡는 시간이라 짧게 잡는다"고 판단한 자원이다
(`TossPaymentProperties`). 그 대기 동안 DB 커넥션과 행 락까지 같이 붙잡으면 커넥션
풀이 먼저 바닥난다. `claimPaymentIfPlaced`는 짧은 `UPDATE` 하나로 끝나고 Toss 호출은
그 바깥에서 일어나므로, 잠그는 대상이 네트워크 왕복 시간이 아니라 그 `UPDATE` 한 번의
시간으로 끝난다.

**왜 `Order` 도메인 모델에 이 필드를 두지 않는가**

`active_payment_id`는 "돈이 얼마인지", "상태가 무엇인지" 같은 업무 규칙이 아니라 순수한
동시성 제어 장치다. `Order.cancel()`/`Order.pay()` 같은 도메인 메서드가 이 값을 읽거나
바꿀 이유가 없으므로, `OrderJpaEntity`(인프라)에만 두고 `Order`(도메인)는 이 컬럼의
존재를 모른다 - `cancelIfPlaced`/`markPaidIfPlaced`가 `Order.status`라는 실제 도메인
필드를 다루는 것과는 성격이 다르다.

## 결과

**얻는 것**

- 같은 주문에 대한 두 결제 시도가 동시에 Toss 승인을 받는 경합이 막힌다 - 실제 이중
  결제(과금)로 이어지는 유일한 경로였다. `ConfirmPaymentConcurrencyTest`로 확인했다.
- `PaymentStatus`/`Payment` 도메인 모델은 전혀 바뀌지 않는다 - 상태 전이 표가 늘지 않는다.
- DB 락을 네트워크 호출 동안 들고 있지 않는다.

**치르는 비용**

- `orders` 테이블에 어떤 도메인 상태도 표현하지 않는 컬럼이 하나 생긴다 - 스키마를
  보는 사람이 "이건 무엇의 상태인가"라고 물을 만한 지점이다.
- Toss 호출 도중(선점 이후, `markDoneIfReady`/`markFailedIfReady` 이전) 서버가 죽으면
  `active_payment_id`가 그 결제 시도로 영원히 남고, 그 주문의 모든 후속 결제 시도가
  막힌다. 이를 되돌리는 정리 배치나 타임아웃은 이번에 만들지 않는다 - 오래된 `READY`
  시도가 정리되지 않는 것([0017](0017-toss-payment-integration.md))과 같은 종류의,
  아직 받아들이는 비용이다.

## 재검토 시점

`active_payment_id`가 실제로 멈춰서 후속 confirm이 막히는 사례가 관측되면,
[0010](0010-expired-data-retention.md)의 정리 배치를 확장하거나 선점에 타임아웃을 둔다
(예: 일정 시간 지난 선점은 강제로 풀어준다). 취소·confirm 사이의 경합
([0017](0017-toss-payment-integration.md)이 남긴 지점)은 이 ADR 이 다루지 않으므로
별도로 재검토한다.
