# 0018. 결제완료 주문의 환불은 기존 취소 API를 확장해 전액만 처리하고, 재고는 복원하지 않는다

- 상태: 수락됨
- 날짜: 2026-09-02
- 관련: [0004](0004-infrastructure-adapter-modules.md), [0014](0014-stock-and-oversell.md),
  [0016](0016-order-domain-scope.md), [0017](0017-toss-payment-integration.md)

## 맥락

[0017](0017-toss-payment-integration.md)이 "결제 완료 주문의 취소는 막는다"고 정하면서
"환불을 자동화하려면 `CancelOrderService`가 `PaymentGateway`의 취소 API를 함께 호출하도록
확장한다"고 재검토 시점에 남겨 둔 지점이다. 이제 그 확장을 한다.

범위를 넷으로 좁혔다 — 별도 환불 API를 새로 만들지 않고 기존 주문 취소 API를 확장한다,
전액 취소만 지원한다(부분 환불 제외), 환불 시 재고는 복원하지 않는다, [0017](0017-toss-payment-integration.md)이
이미 방치하기로 한 결제 확정·취소 사이의 경합은 이번에도 그대로 방치한다.

## 결정

**`OrderStatus`는 늘리지 않는다. 전이표에 `PAID → CANCELLED`만 추가한다.** `REFUNDED`
같은 새 상태를 만들지 않는다 - 구매자 입장에서 "주문이 취소됐다"는 사실은 결제 전이든
후든 하나다. `Order.cancel()`의 사전 검사를 `status != PLACED`에서
`status !in (PLACED, PAID)`로 넓힌다.

**`OrderRepository`에 `cancelIfPaid(id, now): Boolean`을 추가한다.** `cancelIfPlaced`와
같은 조건부 원자 갱신이고(`WHERE status = 'PAID'`), 기존 `cancelIfPlaced`는 그대로 둔다 -
두 경로는 재고 복원 여부와 PG 호출 여부가 갈리므로 하나로 합치지 않는다.

**`PaymentStatus`에 `CANCELLED`를 추가한다(`READY, DONE, FAILED, CANCELLED`).** `DONE`
에서만 전이한다. `PaymentRepository`에 조건부 원자 갱신 `markCancelledIfDone(id, now): Boolean`과
조회 `findDoneByOrderId(orderId): Payment?`를 추가한다. 후자가 필요한 이유는, 환불 흐름은
`tossOrderId`를 모르는 채로 "이 주문의 완료된 결제"를 찾아야 하기 때문이다(기존
`findByOrderIdAndTossOrderId`는 `tossOrderId`를 안다는 전제).

**`PaymentGateway`에 `cancel(paymentKey, cancelReason): PaymentCancellation`을 추가한다.**
Toss 취소 API(`POST /v1/payments/{paymentKey}/cancel`)를 `cancelAmount` 없이 부른다 -
생략하면 전액 취소다. `cancelReason`은 사용자 입력을 받지 않는다(취소 엔드포인트가 지금도
body를 받지 않는다) - 서비스가 고정 문구를 채워 보낸다. 실패하면
`PaymentCancelFailedException(cause)`를 던진다 - `confirm`/`PaymentConfirmFailedException`과
대칭이다.

**`CancelOrderService`가 두 갈래로 나뉜다.** 주문 조회·소유권 검사·`order.cancel(now)`
사전 검사는 그대로 두고, 사전 검사를 통과시킨 **읽어온 시점의 `order.status`**로 분기한다.

- `PLACED`였다면: 기존 그대로 `cancelIfPlaced` + 재고 복원.
- `PAID`였다면:
  1. `payments.findDoneByOrderId(orderId)`로 결제 시도를 찾는다. 없으면(=`PAID`인데
     `DONE` 결제가 없는, 있을 수 없는 상태) 데이터 정합성 문제로 보고 ERROR 로그를 남기고
     예외를 던진다.
  2. **PG 취소를 먼저 부르고, 로컬 상태는 그 다음에 바꾼다.** `paymentGateway.cancel(...)`이
     실패하면 그대로 전파한다 - 이 경우 `Order`는 `PAID`로, `Payment`는 `DONE`으로 남아
     아무것도 취소되지 않은 것으로 보인다.
  3. 성공하면 `payments.markCancelledIfDone(paymentId, now)`, 이어서
     `orders.cancelIfPaid(orderId, now)`. 이 둘이 경합으로 실패해도 PG 환불은 이미
     끝났으므로 조용히 넘기지 않고 ERROR 로그로 남긴다 - `ConfirmPaymentService`가
     `markPaidIfPlaced` 실패를 다루는 것과 대칭이다.
  4. 재고는 복원하지 않는다.

분기가 틀릴 수 있다(읽은 뒤 다른 요청이 상태를 바꿨다) - 그 경우 `cancelIfPlaced`나
`cancelIfPaid`가 `WHERE` 절에 걸려 `false`를 돌려주므로 `OrderNotCancellableException`으로
안전하게 끝난다. 기존 `cancelIfPlaced` 하나만 있을 때와 같은 안전장치다.

**`ErrorCode`에 `PAYMENT_CANCEL_FAILED`를 추가하고, `ErrorCodeHttpStatus` 매핑도 함께
고친다.**

**API 계약은 바뀌지 않는다.** `POST /orders/{id}/cancel`을 그대로 쓴다 - 클라이언트는
결제 여부를 몰라도 된다.

## 근거

**왜 별도 환불 엔드포인트를 새로 만들지 않는가**

구매자 입장에서 "취소"는 하나의 행위다. 결제 전이면 재고를 되돌리고, 결제 후면 돈을
되돌린다는 차이는 서버 내부 사정이다. 기존 취소 API가 소유권 검사·존재 검사·404 통일
규칙([0016](0016-order-domain-scope.md))을 이미 갖추고 있어서, 이를 복제한 새 엔드포인트를
만드는 대신 넓히는 편이 배선을 하나로 유지한다.

**왜 `REFUNDED` 상태를 따로 만들지 않는가**

주문 목록·조회를 쓰는 클라이언트가 상태값이 늘어날 때마다 그 값을 몰라도 되는 화면까지
분기해야 한다. "취소됐다"는 사실 하나로 충분하고, 돈이 실제로 돌아왔는지의 세부는
`Payment` 쪽 정보로 미룬다 - 다만 결제 내역을 노출하는 조회 API가 지금 없으므로, 그
노출 범위는 이번 ADR 밖이다(재검토 시점 참고).

**왜 PG 취소를 먼저 부르고 로컬 상태를 나중에 바꾸는가**

[0017](0017-toss-payment-integration.md)의 `confirm`과 같은 이유다. Toss가 실제로 취소를
처리했는지가 진실의 원천이다. 로컬 `CANCELLED`를 먼저 찍고 PG 호출이 실패하면, 사용자에게는
"취소됐다"고 응답했는데 돈은 돌아오지 않은 상태가 남는다 - 반대로 PG 호출을 먼저 해 두면
실패 시 아무 것도 바뀌지 않은 채로 끝나 재시도가 안전하다.

**왜 부분 환불을 이번에 하지 않는가**

Toss API 자체는 `cancelAmount`로 부분 취소를 지원하지만, 받아들이면 `Payment` 하나가
"얼마가 남았는지"를 들고 있어야 한다(`PARTIAL_CANCELLED` 같은 상태 세분화 또는 잔액
필드). 여기에 `OrderLine` 단위로 무엇을 얼마나 취소했는지까지 요구사항이 뻗을 여지가
크다. 지금 요구사항은 전액 취소뿐이라 이 복잡도를 미리 들이지 않는다.

**왜 환불 시 재고를 복원하지 않는가**

`PLACED` 취소의 재고 복원은 "아직 아무 것도 나가지 않았다"는 전제 위에 있다. 결제
완료 후의 취소는 그 전제가 없다 - 이미 포장·출고 절차가 시작됐을 수 있어, 시스템
재고를 자동으로 되돌리면 실물 재고와 어긋날 위험이 더 크다. 반품·회수 확인이 필요한
문제라 이번 결제 취소 자체와 자동으로 묶지 않는다. 재고를 되돌릴지는 그 확인 절차가
생겼을 때 별도로 정한다.

**왜 confirm·cancel 경합을 이번에도 막지 않는가**

[0017](0017-toss-payment-integration.md)이 "취소가 `PLACED`를 확인하고 재고를 복원하는
사이 confirm이 Toss 승인을 받아 버리는" 좁은 경합을 로그로만 남기고 방치하기로 이미
정했다. 이번 확장이 그 경합의 발생 빈도나 결과를 바꾸지 않는다 - 여전히 같은 초 안에
두 요청이 겹쳐야 하는 좁은 창이고, 여기서 새로 넓히거나 좁힐 이유가 없다.

## 결과

**얻는 것**

- 클라이언트는 주문이 결제 전이든 후든 같은 API로 취소를 요청한다.
- 결제 확정(`confirm`)과 결제 취소(`cancel`) 모두 "PG 호출 먼저, 로컬 상태 반영은 그
  다음"이라는 같은 순서를 따라, 결제 도메인의 쓰기 순서가 하나로 통일된다.
- 조건부 원자 갱신 패턴(`cancelIfPaid`, `markCancelledIfDone`)이 기존 것들과 같은 모양이라
  동시성 처리 방법이 늘지 않는다.

**치르는 비용**

- 부분 환불이 없다 - 결제 금액 일부만 취소하고 싶어도 전액 취소로만 처리된다.
- 재고가 자동으로 복원되지 않는다 - 결제 완료 주문을 취소해도 판매 가능 재고는 그대로다.
- 한 주문에 `DONE` 결제가 둘 이상 존재하는(동시 `confirm` 경합의 결과물) 이상 상태를
  `findDoneByOrderId`가 처리하지 않는다 - 그런 상태가 실제로 생기면 임의의 하나만
  환불되고 나머지는 조용히 남는다.
- PG 취소 호출과 로컬 `CANCELLED` 반영 사이에도 [0017](0017-toss-payment-integration.md)의
  확정·취소 경합과 같은 모양의 좁은 창이 있다 - 환불은 됐는데 로컬 상태가 못 따라가는
  경우 ERROR 로그로만 드러난다.

## 재검토 시점

부분 환불이 요구사항이 되면 `Payment`에 취소 금액 누적 필드를 두고 `PaymentStatus`를
세분화한다. 결제 완료 주문의 환불 후 재고를 되돌릴 정책(반품 확인 후 수동 또는 자동)이
정해지면 이 문서를 갱신한다. 한 주문에 `DONE` 결제가 둘 이상 생기는 경합이 실제로
관측되면 [0017](0017-toss-payment-integration.md)의 재검토 항목과 함께 락 또는 정산
배치로 해결한다.
