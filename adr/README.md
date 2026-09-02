# 아키텍처 결정 기록 (ADR)

되돌리는 비용이 큰 결정을 남긴다. "무엇을 정했는가"보다 **"무엇을 포기했고 왜인가"**가 핵심이다.
코드를 읽어서 알 수 있는 것은 여기 적지 않는다.

## 색인

| 번호 | 제목 | 상태 |
|---|---|---|
| [0001](0001-email-verification-timing.md) | 이메일 인증은 회원가입 이전에 수행한다 | 수락됨 |
| [0002](0002-email-verification-token-design.md) | 인증은 링크 토큰으로 하고 식별자를 둘로 나눈다 | 수락됨 |
| [0003](0003-application-layer-placement.md) | 애플리케이션 유스케이스는 api 모듈에 둔다 | 수락됨 |
| [0004](0004-infrastructure-adapter-modules.md) | 메일과 암호화 어댑터를 별도 인프라 모듈로 분리한다 | 수락됨 |
| [0005](0005-uniqueness-and-email-enumeration.md) | 중복은 DB 제약으로 막고, 가입된 이메일은 409 로 알린다 | 수락됨 |
| [0006](0006-api-response-contract.md) | 성공은 공통 봉투로, 실패는 ProblemDetail 로 응답한다 | 수락됨 |
| [0007](0007-reconstitution-vs-input-validation.md) | 저장된 값의 복원을 입력 검증과 분리한다 | 수락됨 |
| [0008](0008-login-token-strategy.md) | 로그인은 JWT 액세스 토큰과 회전하는 리프레시 토큰으로 유지한다 | 수락됨 |
| [0009](0009-abuse-rate-limiting.md) | 남용 방지 호출 제한은 인메모리 고정 창으로 시작한다 | 수락됨 |
| [0010](0010-expired-data-retention.md) | 만료된 인증과 토큰은 배치로 지운다 | 수락됨 |
| [0011](0011-single-sellable-unit.md) | 상품은 단일 판매 단위로 시작하고 옵션을 두지 않는다 | 수락됨 |
| [0012](0012-money-representation.md) | 금액은 원 단위 정수로 다루고 통화를 두지 않는다 | 수락됨 |
| [0013](0013-product-category-as-enum.md) | 상품 카테고리는 코드 enum 으로 시작한다 | 수락됨 |
| [0014](0014-stock-and-oversell.md) | 재고는 상품이 들고, 차감은 조건부 원자 갱신으로만 한다 | 수락됨 |
| [0015](0015-product-list-pagination.md) | 상품 목록은 커서 페이지네이션으로 낸다 | 수락됨 |
| [0016](0016-order-domain-scope.md) | 주문은 결제·배송 없이 PLACED/CANCELLED 로 시작하고, 소유권 검사는 404 로 감춘다 | 수락됨 |
| [0017](0017-toss-payment-integration.md) | 결제는 Toss 카드 결제만, 동기 confirm 으로 시작하고 결제 완료 주문은 취소를 막는다 | 수락됨 |
| [0018](0018-payment-refund-via-order-cancel.md) | 결제완료 주문의 환불은 기존 취소 API 를 확장해 전액만 처리하고, 재고는 복원하지 않는다 | 수락됨 |
| [0019](0019-concurrent-confirm-claim.md) | 같은 주문의 동시 확정은 주문 행 하나를 선점해서 막는다 | 수락됨 |
| [0020](0020-shipping-domain-scope.md) | 배송지는 주문에 스냅샷하고, 배송은 결제완료 시 자동 생성되는 별도 애그리게이트로 추적한다 | 수락됨 |

## 설계 문서

결정이 아니라 "지금 구조가 어떻게 생겼는가"를 적는다. 코드가 바뀌면 같이 고친다.

- [회원 도메인](design/member-domain.md)
- [상품 도메인](design/product-domain.md)

## 작성 규칙

- 파일명은 `NNNN-english-kebab-case.md`, 내용은 한국어.
  파일명을 영문으로 두는 이유는 macOS 와 Linux 의 한글 유니코드 정규화(NFD/NFC)가 달라
  git 이 동일 파일을 다른 경로로 인식하는 사고를 피하기 위해서다.
- 번호는 재사용하지 않는다. 결정이 뒤집히면 기존 문서를 지우지 말고
  상태를 `대체됨(→ NNNN)` 으로 바꾸고 새 번호를 발급한다.
- 상태: `제안됨` / `수락됨` / `대체됨` / `폐기됨`
