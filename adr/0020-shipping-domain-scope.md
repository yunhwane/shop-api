# 0020. 배송지는 주문에 스냅샷하고, 배송은 결제완료 시 자동 생성되는 별도 애그리게이트로 추적한다

- 상태: 수락됨
- 날짜: 2026-09-02
- 관련: [0007](0007-reconstitution-vs-input-validation.md), [0011](0011-single-sellable-unit.md),
  [0016](0016-order-domain-scope.md), [0017](0017-toss-payment-integration.md),
  [0018](0018-payment-refund-via-order-cancel.md)

## 맥락

배송 도메인을 처음 넣는다. 두 기능을 요구받았다 - 주소지 관리, 배송 추적. 범위를 넷으로
좁혔다 - 주소록 없이 주문 시 1회성 입력만 받는다, 배송 추적은 실제 택배사 연동 없이 자체
상태 전이만 다룬다, 배송 상태를 바꾸는 쓰기는 내부 유스케이스로만 두고 HTTP 로 열지
않는다, 주문과 배송은 1:1 이다(부분배송 없음).

세 번째 결정은 새로운 게 아니다 - `ProductRegistrationService`/`ProductManagementService`
가 이미 같은 이유로 컨트롤러를 갖지 않는다: "권한 모델이 없는 상태에서 쓰기 엔드포인트를
열면 인증만으로는 인가가 되지 않아 로그인한 아무 회원이나 고칠 수 있다."

## 결정

**`ShippingAddress` 값 객체를 새 패키지 `core.domain.shipping` 에 둔다.** 수령인 이름,
전화번호, 우편번호, 기본주소, 상세주소(선택) 를 담는다. 저장되는 값이라 `of`/`reconstitute`
를 둘 다 갖는다(ADR 0007). `Order`(스냅샷의 입력)와 `Shipment`(스냅샷의 보관) 양쪽에서
같은 타입을 그대로 쓴다 - `OrderLine` 이 `core.domain.product.ProductName` 을 그대로
쓰는 것과 같은 모양의 패키지 간 참조다.

```
ShippingAddress(
  recipientName: String,   // 1~50자
  phone: String,           // 숫자와 하이픈, 9~13자
  postalCode: String,      // 5자리 숫자
  addressLine1: String,    // 1~200자
  addressLine2: String?,   // 1~100자, 없으면 null
)
```

**`Order` 가 배송지를 갖는다.** `Order.place()` 가 `shippingAddress: ShippingAddress` 를
필수로 받는다 - 주소록이 없으므로 주문할 때마다 매번 입력받는다. 주문이 만들어진 뒤에는
바꿀 수 없다(수정 API 를 이번에 두지 않는다).

**`Shipment` 을 `Order` 와 분리된 애그리게이트로 둔다.** `Payment` 가 `orderId` 만
참조하는 것과 같은 이유다(ADR 0017) - 배송은 결제와 독립된 생명주기를 갖고, `Order` 는
이 애그리게이트의 존재를 몰라도 된다.

```
Shipment(
  id, orderId,
  address: ShippingAddress,  // Order.shippingAddress 를 그 시점에 스냅샷
  status: ShipmentStatus,    // PREPARING, SHIPPING, DELIVERED
  shippedAt: Instant?,
  deliveredAt: Instant?,
  createdAt, updatedAt,
)
```

**`Shipment` 은 결제 확정 시 자동으로 생성된다.** 별도로 "배송 준비 시작" 을 호출하는
행위자가 없으므로(관리자 API 가 없다), `ConfirmPaymentService` 가 `orders.markPaidIfPlaced`
성공 직후 `Shipment.prepare(orderId, order.shippingAddress, now)` 를 만들어 저장한다.
**이 저장이 실패해도 결제 확정 자체는 롤백하지 않는다** - 실패를 잡아 ERROR 로그로만
남긴다. Toss 는 이미 승인했으므로, 배송 준비 생성이라는 부차적인 쓰기가 그 사실을
되돌리게 두지 않는다.

**배송 상태 전이는 내부 유스케이스로만 존재한다.** `api.shipment.application.ShipmentTrackingService`
에 `startShipping(orderId)`, `markDelivered(orderId)` 를 두되 컨트롤러가 없다 -
`ProductManagementService` 와 같은 이유, 같은 문구다. 지금은 테스트만 이 유스케이스를
부른다. 상태 전이 사전 검사는 `DomainException`/`ErrorCode` 가 아니라 평범한 `check()`
로 한다 - HTTP 로 절대 나가지 않는 예외에 응답 계약(ADR 0006)을 씌울 이유가 없다.

`ShipmentRepository` 는 조건부 원자 갱신 두 개를 갖는다 - `startShippingIfPreparing`,
`markDeliveredIfShipping`. 이 코드베이스의 다른 모든 상태 전이와 같은 모양이다
(ADR 0014, 0016, 0017).

**배송 조회는 읽기 전용으로 HTTP 에 연다.** `GET /api/v1/orders/{orderId}/shipment` 를
추가한다 - 상태를 바꾸는 쓰기와 자기 배송을 확인하는 읽기는 다른 문제다. 본인 주문이
아니면 기존 규칙대로 `ORDER_NOT_FOUND` 다(ADR 0016). 본인 주문인데 아직 결제 전이라
배송이 없으면 새 `SHIPMENT_NOT_FOUND` 를 404 로 답한다 - 이건 존재를 숨길 이유가 없는
정보라 `ORDER_NOT_FOUND` 로 뭉개지 않는다.

**`ShipmentStatus` 는 셋뿐이다: `PREPARING`, `SHIPPING`, `DELIVERED`.** `CANCELLED` 를
두지 않는다 - 결제완료 주문의 환불(ADR 0018)이 이미 나간 배송과 어떻게 맞물리는지는
이번 범위 밖이다(재검토 시점 참고).

## 근거

**왜 주소록을 만들지 않는가**

재사용 가능한 주소록은 그 자체로 작은 CRUD 도메인이다(등록·수정·삭제·기본 주소 지정).
지금 요구사항은 "주문할 때 배송지를 입력한다"는 것뿐이라, 그 이상을 미리 짓지 않는다 -
[0011](0011-single-sellable-unit.md)이 상품 옵션을 미리 만들지 않은 것과 같은 판단
기준이다.

**왜 `Order` 에 값을 두고 `Shipment` 에도 다시 스냅샷하는가**

지금 범위에서 `Order.shippingAddress` 는 생성 후 바뀌지 않으니 중복처럼 보인다. 그래도
`Payment.amount` 가 `Order.totalAmount` 를 스냅샷하는 것과 같은 이유로 나눈다 - `Shipment`
가 `Order` 를 다시 읽지 않고도 자기 완결적으로 남아야, 나중에 배송 조회·정산·택배사
연동이 `Order` 를 몰라도 된다. 지금은 두 값이 항상 같지만, 그 전제가 깨질 다음 변경
(주소 수정 API, 재배송)이 생겨도 이 구조는 그대로 견딘다.

**왜 배송 준비 생성 실패가 결제 확정을 되돌리면 안 되는가**

`ConfirmPaymentService` 는 이미 Toss 승인 → `Payment` DONE → `Order` PAID 순으로 쓰기를
쌓는다. `Shipment` 저장은 그 뒤에 붙는 네 번째 쓰기이자, 성격이 다르다 - 앞의 셋은 "돈이
실제로 오갔다"는 사실이고, 이건 그 사실에 곁달린 부수 효과다. 같은 트랜잭션에서 예외를
그대로 던지면 부수 효과의 실패가 사실 자체를 지운다 - Toss 는 이미 승인했는데 로컬에는
아무 기록도 안 남는, `PaymentConfirmFailedException` 을 `noRollbackFor` 로 막아 둔 것과
정반대 방향의 같은 문제다. 그래서 여기서는 롤백 대신 잡아서 로그로 남긴다.

**왜 배송 상태 전이에 `DomainException`/`ErrorCode` 를 쓰지 않는가**

ADR 0006 의 실패 응답 계약(`ProblemDetail`)은 HTTP 경계를 건너는 예외를 위한 것이다.
`ShipmentTrackingService` 는 컨트롤러가 없어 그 경계를 건널 일이 없으므로, 무거운
`ErrorCode` + `ErrorCodeHttpStatus` 매핑을 갖출 이유가 없다 - `OrderRepositoryAdapter.save()`
가 이미 있는 주문을 받으면 `IllegalStateException` 으로 답하는 것과 같은 판단이다.
컨트롤러가 생기는 시점에 그때 필요한 예외 타입으로 바꾼다.

**왜 배송 조회는 열어 두는가**

"배송 추적 기능"은 구매자가 자기 주문이 지금 어디쯤인지 볼 수 있어야 의미가 있다. 상태를
**바꾸는** 권한은 없어도, 자기 주문을 **읽는** 권한은 `OrderQueryService` 가 이미 갖고 있는
것과 같은 성격이다(ADR 0016) - 그래서 조회 엔드포인트는 관리자 권한 모델을 기다리지 않고
바로 연다.

**왜 `CANCELLED` 상태를 만들지 않는가**

결제완료 주문도 환불될 수 있다(ADR 0018). 그런데 그 주문에 이미 배송이 `SHIPPING` 이나
`DELIVERED` 까지 간 상태라면 "환불했으니 배송도 취소"가 실물 배송과 맞지 않는다 - 반품
회수 절차가 필요한 문제이지 상태 하나로 표현할 성격이 아니다. 결제 도메인이 처음
`PAID` 를 만들 때 배송 상태를 미리 만들지 않았던 것([0016](0016-order-domain-scope.md))과
같은 이유로, 실제로 그 상호작용을 다뤄야 하는 시점까지 미룬다.

## 결과

**얻는 것**

- 배송 준비가 결제 성공에 자동으로 뒤따라, 주문마다 "배송 시작을 깜빡하는" 수동 단계가
  없다.
- `Shipment` 를 `Payment` 와 같은 모양(분리된 애그리게이트, 조건부 원자 갱신, orderId 참조)
  으로 만들어, 새 도메인을 읽는 사람이 이미 아는 패턴을 그대로 적용할 수 있다.
- 구매자는 자기 배송 상태를 바로 확인할 수 있다.

**치르는 비용**

- 배송 상태를 실제로 바꿀 방법이 이번 범위에는 없다 - 관리자 인증이 생기기 전까지는
  테스트에서만 `ShipmentTrackingService` 를 부를 수 있다. 실제 운영에서는 이 유스케이스를
  누가, 어떻게 부를지 정해지지 않았다.
- 환불된 주문의 배송을 어떻게 다룰지 정하지 않았다 - `Shipment` 는 결제가 취소돼도
  `PREPARING`/`SHIPPING`/`DELIVERED` 중 하나로 그대로 남는다.
- 배송지를 잘못 입력해도 고칠 방법이 없다(수정 API 없음). 주문을 취소하고 다시 하는
  수밖에 없다.
- 배송 준비 생성이 실패하면 결제는 확정됐는데 배송 기록만 없는 상태가 로그로만 남는다
  (위 근거 참고).

## 재검토 시점

관리자·판매자 권한 모델이 생기면 `ShipmentTrackingService` 에 컨트롤러를 얹고, 배송
상태 변경 실패를 `DomainException`/`ErrorCode` 로 옮긴다. 실제 택배사 연동이 필요해지면
`ShipmentGateway` 포트를 추가하고 `PaymentGateway`(ADR 0017)와 같은 어댑터 모듈 구조를
따른다. 배송지 재사용(주소록)이 요구되면 별도 도메인으로 뽑고, `Order.place()` 는 그
주소록에서 고른 주소를 스냅샷받는 쪽으로 바뀐다. 결제완료 주문의 환불과 이미 진행 중인
배송이 부딪히는 사례가 실제로 생기면, `ShipmentStatus` 에 `CANCELLED` 를 추가할지 반품
절차를 별도로 둘지 그때 정한다.
