# 0006. 성공은 공통 봉투로, 실패는 ProblemDetail 로 응답한다

- 상태: 수락됨
- 날짜: 2026-09-01
- 관련: [0005](0005-uniqueness-and-email-enumeration.md)

## 맥락

**예외 응답도 계약이다.** 클라이언트는 상태 코드만으로 분기할 수 없다.
회원가입만 해도 `409` 안에 `DUPLICATE_USER_ID` / `DUPLICATE_EMAIL` /
`VERIFICATION_ALREADY_USED` 세 가지가 들어간다. 각 컨트롤러가 제 방식대로 에러를
만들면 클라이언트는 엔드포인트마다 다른 파싱을 해야 한다.

동시에 성공 응답도 모양이 일정해야 한다. 어떤 API 는 객체를, 어떤 API 는 배열을,
어떤 API 는 원시값을 그대로 반환하면 클라이언트의 공통 처리 계층을 만들 수 없다.

## 결정

**두 포맷을 나눈다.**

| | Content-Type | 형태 |
|---|---|---|
| 성공 (2xx) | `application/json` | `ApiResponse<T>` 봉투 |
| 실패 (4xx/5xx) | `application/problem+json` | RFC 9457 `ProblemDetail` |

```json
// 성공
{ "data": { "id": 1, "userId": "alice01" } }

// 실패
{
  "type": "/problems/duplicate-user-id",
  "title": "이미 사용 중인 아이디입니다.",
  "status": 409,
  "detail": "userId 'alice01' 은 이미 사용 중입니다.",
  "instance": "/api/v1/users",
  "code": "DUPLICATE_USER_ID",
  "timestamp": "2026-09-01T04:12:33Z"
}
```

실패 응답을 `ApiResponse` 봉투에 넣지 않는다.

## 근거

**왜 실패는 봉투를 쓰지 않는가**

ProblemDetail 의 값은 **표준이라는 점**에 있다. `type` / `title` / `status` /
`detail` / `instance` 라는 필드 이름이 RFC 9457 로 고정되어 있어, 클라이언트 라이브러리와
API 게이트웨이, 모니터링 도구가 별도 설정 없이 읽는다. 이걸 `{ "data": ..., "error": ... }`
같은 자체 봉투로 감싸는 순간 그 호환성이 통째로 사라진다. 표준을 쓰면서 표준으로
인식되지 않게 만드는 셈이다.

Spring 도 같은 전제로 움직인다. `ResponseEntityExceptionHandler` 가 처리하는 내장 예외
(바인딩 실패, 미지원 미디어 타입 등)는 이미 `ProblemDetail` 로 나간다. 봉투를 강제하면
**우리가 안 짠 예외까지 전부 가로채서 다시 감싸야 한다.**

**왜 성공 응답에 `success` 플래그를 두지 않는가**

두 포맷이 Content-Type 으로 갈리므로 `success: true` 는 항상 참이다.
항상 참인 필드는 정보가 없다. HTTP 상태 코드가 이미 그 역할을 한다.

**왜 봉투를 아예 없애지 않는가**

`data` 한 겹이 있으면 나중에 페이지네이션 `meta` 나 커서를 **기존 필드를 건드리지 않고**
붙일 수 있다. 최상위에 객체를 그대로 노출하면 그때 응답 구조가 깨진다.
최상위 배열 반환을 원천 차단하는 효과도 있다.

## 적용 방식

**봉투는 컨트롤러가 명시적으로 씌운다.** `ResponseBodyAdvice` 자동 래핑을 쓰지 않는다.

```kotlin
@PostMapping
fun signUp(@RequestBody request: SignUpRequest): ResponseEntity<ApiResponse<SignUpResponse>> =
    ResponseEntity.status(CREATED).body(ApiResponse.of(service.signUp(request.toCommand())))
```

자동 래핑은 편해 보이지만 대가가 크다.

- `ProblemDetail` 응답을 반드시 예외 처리해야 한다. 조건 분기가 어드바이스 안에 쌓인다.
- `String` 반환값은 `StringHttpMessageConverter` 가 먼저 잡아 `ClassCastException` 이 난다.
- 메서드 시그니처와 실제 응답 본문이 달라진다. OpenAPI 스키마가 거짓말을 하고,
  컨트롤러 테스트에서 무엇이 나갈지 코드만 보고 알 수 없다.

보일러플레이트 한 줄이 이 셋보다 싸다.

## 에러 코드 체계

`code` 는 **RFC 9457 의 확장 필드**다. `type` URI 로도 식별은 되지만, 클라이언트가
URI 문자열을 파싱해 분기하는 것보다 안정적인 심볼 하나를 주는 편이 낫다.

코드는 `core-enum` 의 `ErrorCode` 에 모은다.

```
core-enum     ErrorCode          코드 심볼과 기본 메시지. "무엇이 잘못됐는가"
core-domain   DomainException    ErrorCode 를 들고 던진다. HTTP 를 모른다
api           ErrorCodeHttpStatus  ErrorCode → HttpStatus. "어떻게 표현하는가"
              GlobalExceptionHandler
```

**상태 코드 매핑을 `api` 에 두는 것이 핵심이다.** `409` 냐 `400` 이냐는 HTTP 의 관심사이므로
도메인이 알 이유가 없다. `core` 는 Spring 에 의존할 수 없다는 기존 규칙과도 맞는다.

## 결과

**얻는 것**

- 클라이언트가 `code` 하나로 분기한다. 엔드포인트별 파싱이 없다.
- Spring 내장 예외와 우리 도메인 예외가 **같은 모양**으로 나간다.
- 새 에러를 추가하려면 `ErrorCode` 와 매핑표에만 손대면 된다.

**치르는 비용**

- 컨트롤러마다 `ApiResponse.of(...)` 를 적어야 한다.
- 성공/실패의 최상위 구조가 다르다. 클라이언트는 상태 코드로 먼저 갈라야 한다
  (대부분의 HTTP 클라이언트가 이미 그렇게 동작한다).
- `ErrorCode` 를 추가할 때 `api` 의 매핑표도 같이 고쳐야 한다. 빠뜨리면 기본값
  `500` 으로 나가므로, **매핑 누락을 잡는 테스트를 둔다.**

## 규율

- 도메인 예외는 반드시 `DomainException` 을 상속하고 `ErrorCode` 를 갖는다.
- `detail` 에 **사용자 입력값을 그대로 넣지 않는다.** 비밀번호나 토큰이 섞이면
  그대로 로그와 클라이언트에 노출된다.
- `500` 응답의 `detail` 에는 내부 예외 메시지를 넣지 않는다. 스택트레이스는 로그로만.
