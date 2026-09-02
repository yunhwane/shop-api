# 0017. 결제는 Toss 카드 결제만, 동기 confirm 으로 시작하고 결제 완료 주문은 취소를 막는다

- 상태: 수락됨
- 날짜: 2026-09-02
- 관련: [0004](0004-infrastructure-adapter-modules.md), [0007](0007-reconstitution-vs-input-validation.md),
  [0012](0012-money-representation.md), [0014](0014-stock-and-oversell.md), [0016](0016-order-domain-scope.md)

## 맥락

주문 도메인에 결제를 붙인다. PG 는 Toss Payments 로 정했다. [0016](0016-order-domain-scope.md)
이 "결제 도메인이 생기면 `OrderStatus` 를 확장한다"고 미뤄 둔 지점이다.

범위를 셋으로 좁혔다 — 카드 결제만(가상계좌 제외), 결제 전 주문만 취소 가능(결제 완료
주문의 자동 환불 없음), 웹훅 없이 동기 confirm 만.

## 결정

**`OrderStatus` 에 `PAID` 를 추가한다.** `PLACED → PAID`(결제 확정), `PLACED → CANCELLED`(기존)
만 있고 `PAID` 에서는 전이가 없다. `Order.cancel()` 은 이미 `status != PLACED` 면 거부하므로,
결제 완료 주문의 취소 차단은 코드 변경 없이 그대로 성립한다.

**`Payment` 를 `Order` 와 분리된 애그리게이트로 둔다.** `OrderLine` 이 `Product` 를 참조 대신
스냅샷하는 것과 같은 이유로, `Payment` 는 `Order` 객체가 아니라 `orderId` 만 참조한다.

```
Payment(
  id, orderId,
  tossOrderId: TossOrderId,  // 결제 시도마다 새로 발급하는 식별자
  amount: Money,              // ready 시점에 Order.totalAmount 를 스냅샷 - 위변조 대조 기준
  status: PaymentStatus,      // READY, DONE, FAILED
  paymentKey: PaymentKey?,    // confirm 성공 후 Toss 가 준 값
  approvedAt, createdAt, updatedAt,
)
```

`tossOrderId` 로 `Order.id` 를 그대로 못 쓰는 이유는, Toss 가 결제 시도마다 새로운 식별자를
요구하기 때문이다 - 실패 후 재시도가 같은 값을 다시 쓰면 Toss 쪽에서 혼선을 일으킨다.
`ready` 를 부를 때마다 새 `Payment(READY)` 행을 만든다. 오래된 `READY` 행을 지우는 배치는
이번에 만들지 않는다 - [0010](0010-expired-data-retention.md) 의 정리 배치를 확장할 대상으로
남겨 둔다.

**흐름은 두 단계다.**

1. `POST /orders/{id}/payments` - 본인 소유 `PLACED` 주문만 허용. `tossOrderId` 를 발급하고
   `Payment(READY)` 를 저장한다. 프론트가 이 값으로 Toss 위젯을 연다.
2. `POST /orders/{id}/payments/confirm` - body 로 받은 `tossOrderId`/`paymentKey`/`amount` 로
   `Payment` 를 조회한다. **클라이언트가 보낸 `amount` 를 신뢰하지 않고** 서버가 기록해 둔
   `Payment.amount` 와 대조부터 한다 - 다르면 Toss 를 부르지 않고 즉시 거부한다. 이미 `DONE`
   이면 Toss 를 다시 부르지 않고 기존 결과를 그대로 돌려준다(이중 클릭·재시도에 대한 멱등).
   통과하면 `PaymentGateway.confirm(...)` 을 부르고, 성공하면 `Payment` 를 `DONE` 으로,
   `OrderRepository.markPaidIfPlaced` 로 주문을 `PAID` 로 조건부 원자 갱신한다.

**포트와 어댑터는 메일과 같은 모양이다(ADR 0004).** `core.domain.port.PaymentGateway` 는
`confirm(...)` 시그니처만 갖고 Toss 를 모른다. 새 인프라 모듈
`infrastructure:client-payment-toss`(`com.example.shopapi.client.payment.toss`) 가 구현하고,
`api` 는 `runtimeOnly` 로만 참조한다. 시크릿 키는 환경변수 `TOSS_SECRET_KEY` 로 주입한다
(`RESEND_API_KEY` 와 같은 방식).

## 근거

**왜 `Payment` 를 `Order` 의 필드가 아니라 별도 애그리게이트로 두는가**

결제는 실패하고 재시도된다. 주문 하나에 결제 시도가 여러 번 붙을 수 있는데, `Order` 안에
배열로 넣으면 `OrderJpaEntity` 가 이미 라인에 쓰고 있는 `@ElementCollection` 패턴과 충돌하고,
"몇 번째 시도가 유효한가"를 매번 계산해야 한다. 독립된 테이블에 `orderId` 로 걸면 조회가
단순해지고, `OrderRepository.markPaidIfPlaced` 하나로 `Order` 쪽 상태 갱신을 격리할 수 있다.

**왜 클라이언트가 보낸 금액을 믿지 않는가**

Toss 위젯의 결제창 리다이렉트는 브라우저를 거친다. `amount` 쿼리 파라미터를 그대로 신뢰하면
사용자가 개발자 도구로 값을 바꿔 더 싼 금액을 confirm 요청에 실어 보낼 수 있다. Toss 의
confirm API 자체도 자신이 승인한 금액과 다르면 거부하지만, 그 확인에 기대기 전에 서버가 먼저
`Payment.amount`(ready 시점에 서버가 기록한 값)와 대조해 불일치를 걸러낸다 - 방어선을 하나로
두지 않는다.

**왜 confirm 을 멱등하게 만드는가**

네트워크 재시도, 이중 클릭, 브라우저의 요청 재전송이 흔하다. 이미 `DONE` 인 결제에 다시
confirm 이 오면 Toss 를 또 부르는 대신 기존 결과를 돌려준다 - 이미 승인된 `paymentKey` 로
Toss 를 다시 부르면 Toss 가 거부하고, 그 실패를 사용자에게 그대로 보여주면 실제로는 성공한
결제가 실패로 보인다.

**결제 완료·취소 사이의 좁은 경합을 이번에는 막지 않는다**

주문 취소는 `PLACED` 일 때만 되고, confirm 도 `PLACED` 를 전제로 Toss 를 부른다. 두 요청이
동시에 들어오면 - 취소가 먼저 `cancelIfPlaced` 로 주문을 `CANCELLED` 로 옮기고 재고를
복원하는 사이, confirm 은 이미 Toss 승인을 받아 버렸을 수 있다. 이 경우 `markPaidIfPlaced` 가
`false` 를 돌려주고, 서버는 **돈은 실제로 받았는데 주문은 `PAID` 로 옮겨지지 않은** 상태를
로그로 남긴다(에러 레벨, 수동 정산 필요). 이 창을 완전히 막으려면 confirm 이 주문 행을
잠그거나, 취소가 진행 중인 결제 시도의 존재를 확인해야 하는데 둘 다 이번 범위(웹훅 없는
동기 confirm) 를 넘어선다. 실제로 같은 초 안에 두 요청이 겹쳐야 하는 좁은 창이라 지금은
로그로 드러내는 것으로 그친다.

## 결과

**얻는 것**

- 재고 차감·주문 취소와 같은 조건부 원자 갱신 패턴을 결제 확정에도 그대로 적용해,
  동시성 처리 방법이 코드베이스 전체에서 하나로 통일된다.
- `PaymentGateway` 포트 뒤에 Toss 를 가둬서, `api` 는 여전히 어떤 PG 를 쓰는지 모른다.

**치르는 비용**

- 결제 완료·취소 사이의 경합 창을 완전히 막지 않는다(위 근거 참고). 발생하면 로그로만
  드러나고 정산은 수동이다.
- 가상계좌, 부분 환불, 웹훅 기반 비동기 상태 반영이 전부 빠졌다. 결제 완료 주문의 취소도
  막아 뒀을 뿐 자동 환불로 이어지지 않는다.
- 오래된 `READY` 결제 시도가 정리되지 않고 쌓인다.

## 재검토 시점

가상계좌나 부분 환불이 요구사항이 되면 웹훅 엔드포인트와 서명 검증을 추가하고, 그때
`PaymentStatus` 에 상태가 더 늘어난다. 결제 완료 주문의 환불을 자동화하려면
`CancelOrderService` 가 `PaymentGateway` 의 취소 API 를 함께 호출하도록 확장한다. 위에서
받아들인 경합 창이 실제로 발생하는 사례가 생기면, `OrderRepository` 에 결제 확정 전용 락을
추가하거나 정산 배치로 감지하는 절차를 만든다.
