# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 언어

저장소의 문서, 코드 주석, 커밋 메시지는 모두 한국어로 작성한다. 새로 쓰는 것도 한국어로 맞춘다.

## 명령어

```bash
./gradlew build                      # 컴파일 + ktlintCheck + 전체 테스트 (CI 와 동일)
./gradlew :api:bootRun               # 애플리케이션 실행
./gradlew ktlintFormat               # 포맷 자동 수정
./gradlew ktlintCheck                # 포맷 검사만

./gradlew :tests:architecture:test   # 아키텍처 규칙만 검증
./gradlew :api:test                  # 특정 모듈 테스트
./gradlew :core:core-domain:test     # 도메인 단위 테스트. Spring 없이 돈다

# API 문서. build 에 포함되며 tests/api-docs/build/docs/asciidoc/index.html 에 나온다
./gradlew :tests:api-docs:asciidoctor

# Resend API 키 없이 실행. 인증 메일 대신 링크가 로그로 나온다
./gradlew :api:bootRun --args='--mail.provider=log'

# 단일 테스트 클래스 / 메서드
./gradlew :tests:architecture:test --tests 'com.example.shopapi.architecture.ModuleDependencyTest'
./gradlew :api:test --tests 'ShopApiApplicationTests.contextLoads'
```

테스트 이름이 백틱으로 감싼 한글 서술형인 경우 `--tests '*.ModuleDependencyTest'` 처럼 클래스 단위로 거는 편이 안전하다.

### JDK

`jvmToolchain(25)` 를 요구하는데 **Gradle 이 툴체인을 자동으로 내려받지 못한다.** `settings.gradle.kts` 에 foojay toolchain resolver 가 없어서, 로컬에 JDK 25 가 실제로 있어야 빌드가 뜬다. (README 의 "Gradle 툴체인이 자동으로 잡습니다"는 현재 사실이 아니다. resolver 를 넣든 문서를 고치든 한쪽으로 맞춰야 한다.)

macOS 에서 없을 때:

```bash
brew install openjdk@25    # cask(temurin@25)는 sudo 가 필요해 비대화형 셸에서 막힌다
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew build
```

keg-only 라 PATH 에 잡히지 않는다. `JAVA_HOME` 을 매번 주거나 `gradle.properties` 의 `org.gradle.java.home` 에 박아 둔다.

## 아키텍처

Kotlin + Spring Boot 멀티모듈. 헥사고날 방향의 의존 규칙이 빌드 설정과 ArchUnit 테스트 양쪽으로 강제된다.

```
api ──implementation──> core:core-domain ──api──> core:core-enum
 └───runtimeOnly──────> infrastructure:{storage-db, security, client-mail}
                             └──implementation──> core:core-domain
```

| 모듈 | 패키지 | 역할 |
|---|---|---|
| `core:core-enum` | `com.example.shopapi.core.enums` | 전 계층 공유 enum/상수. 외부 의존성 없음 |
| `core:core-domain` | `com.example.shopapi.core.domain` | 도메인 모델, 값 객체, 포트 |
| `infrastructure:storage-db` | `com.example.shopapi.storage` | JPA 구현. 유니크 제약 위반을 도메인 예외로 번역 |
| `infrastructure:security` | `com.example.shopapi.security` | bcrypt 해싱, JWT 서명, 토큰 생성 |
| `infrastructure:client-mail` | `com.example.shopapi.client.mail` | `MailSender` 구현과 메일 템플릿 |
| `api` | `com.example.shopapi.api` | 실행 모듈. controller / dto / 유스케이스 / 설정 |
| `tests:architecture` | `com.example.shopapi.architecture` | ArchUnit 규칙 검증 전용. 프로덕션 코드 없음 |
| `tests:api-docs` | `com.example.shopapi.apidocs` | REST Docs 문서 생성 전용. 프로덕션 코드 없음 |

핵심 제약 — 새 코드를 넣을 위치를 정할 때 이것부터 확인한다:

- `core:*` 는 Spring / JPA / Hibernate 에 의존하지 않는다. 순수 Kotlin 이다.
- `api` 는 인프라 모듈 **셋 모두**를 `runtimeOnly` 로만 참조한다. 구현체를 컴파일 타임에 import 할 수 없고, 빈은 컴포넌트 스캔으로 주입된다(`ShopApiApplication` 이 루트 패키지에 있어 하위 인프라 패키지를 전부 훑는다). `api` 에서 어댑터가 안 보이는 것은 버그가 아니라 의도다.
- 각 인프라 모듈은 자기 `@Configuration` 과 `application-*.properties` 를 스스로 들고 온다. 새 어댑터 모듈도 같은 방식으로 자급한다.
- `@Entity` 와 Spring Data 리포지토리는 `storage` 패키지에만, `@RestController` 는 `api` 패키지에만 둔다.
- 필드 주입 금지(생성자 주입만), `println` 금지(로거 사용), public 가변 필드 금지.
- `tests/*` 는 항상 리프 모듈이다. 전 모듈을 참조하되 아무도 이들을 참조하지 않는다.

위 규칙은 `tests/architecture` 의 `ModuleDependencyTest`(모듈 경계·계층·순환)와 `CodingConventionTest`(프레임워크 컨벤션)가 검사한다. 규칙 추가는 두 파일에 `@ArchTest val` 을 넣고, 패키지 상수는 `Packages.kt` 한 곳에서 관리한다.

### 아키텍처 규칙을 추가할 때의 함정 둘

`archRule.failOnEmptyShould=true` 다. 검사 대상이 0건인 규칙은 실패한다 — 패키지명을 잘못 적었거나 모듈을 규칙에 등록하지 않아 규칙이 헛도는 상태를 잡기 위해서다. 대상이 없는 것이 정상인 **예방 규칙**(예: `public 가변 필드를 두지 않는다` — Kotlin 프로퍼티는 private 필드로 컴파일된다)에는 개별적으로 `.allowEmptyShould(true)` 를 붙인다.

**단순 이름으로 판별하는 규칙은 프레임워크 애노테이션까지 잡는다.** `haveSimpleNameEndingWith("Controller")` 는 `@RestController` 애노테이션 클래스 자체와도 일치해서, 모든 컨트롤러가 위반이 된다. 대상을 `resideInAPackage(API)` 로 좁혀야 한다.

새 어댑터 모듈을 만들면 `Packages.INFRASTRUCTURE` 에 넣어야 경계 규칙이 함께 걸린다. 계층 규칙은 `optionalLayer` 가 아닌 `layer` 라 빈 계층도 실패한다.

### 새 코드를 어디에 둘 것인가

- **도메인 모델·값 객체** → `core:core-domain`. 형식 검증과 정규화는 값 객체 팩토리에서 한다.
  저장되는 값에는 팩토리가 둘 필요하다 — 입력용 `of(raw)` 와 복원용 `reconstitute(stored)`. 검증은 같고 실패가 각각 400 / 500 으로 갈린다(ADR 0007).
- **바깥으로 나가는 모든 구멍** → `core.domain.port` 한 패키지. 리포지토리든 메일이든 시계든 여기 인터페이스로 선언하고 인프라 모듈이 구현한다. 시그니처에 프레임워크 타입이 등장하면 안 된다.
- **유스케이스(애플리케이션 서비스)** → `api/<도메인>/application`. `core` 는 Spring 에 의존할 수 없어 `@Service` / `@Transactional` 을 붙일 수 없다. 두 번째 진입점(배치·컨슈머)이 같은 유스케이스를 필요로 하면 그때 별도 모듈로 뽑는다(ADR 0003).
- **컨트롤러** → DTO ↔ 도메인 변환과 HTTP 관심사만. 분기 로직을 넣지 않는다.

### 응답 계약

성공과 실패의 포맷이 다르다(ADR 0006).

- 성공(2xx): `application/json`, `ApiResponse<T>` 봉투. 컨트롤러가 `ApiResponse.of(...)` 로 **명시적으로** 씌운다. `ResponseBodyAdvice` 자동 래핑을 쓰지 않는다.
- 실패(4xx/5xx): `application/problem+json`, RFC 9457 `ProblemDetail`. `GlobalExceptionHandler` 가 전담한다.

에러를 추가하려면 `core-enum` 의 `ErrorCode` 와 `api` 의 `ErrorCodeHttpStatus` 매핑표를 **둘 다** 고친다. 상태 코드가 `api` 에 있는 이유는 409 냐 400 이냐가 HTTP 의 관심사이기 때문이다. 매핑을 빠뜨리면 조용히 500 으로 나가므로 `ErrorCodeHttpStatusTest` 가 누락을 잡는다.

### 인증

액세스 토큰(JWT)과 회전하는 리프레시 토큰을 쓴다(ADR 0008). 서명과 해싱은
`infrastructure:security` 안에 있고 `api` 는 포트만 본다. 필터 체인은 웹 관심사라 `api` 에 둔다.

인증 실패는 Spring Security 의 필터 단계에서 일어나 기본값으로는 `@RestControllerAdvice` 를
타지 않는다. `AuthenticationEntryPoint` 가 `HandlerExceptionResolver` 로 넘겨 나머지와 같은
ProblemDetail 로 만든다. 이 배선을 빼면 인증 실패 응답만 계약을 벗어난다.

**트랜잭션 롤백이 보안 조치를 지울 수 있다.** 리프레시 토큰 재사용을 탐지해 토큰을 폐기하고
예외를 던지면, 그 예외가 폐기까지 롤백시킨다. `noRollbackFor` 로 막아 두었다. 401 은 나가므로
테스트가 초록불이기 쉽다 — 폐기가 실제로 남았는지까지 확인해야 한다.

### 남용 방지와 정리 배치

호출 제한은 인메모리 고정 창이다(ADR 0009). **인스턴스마다 카운터가 따로이고 재시작하면
초기화된다.** 서버를 늘리는 순간 실질 한도가 대수만큼 곱해지므로 공유 저장소로 옮겨야 한다.
`X-Forwarded-For` 는 읽지 않는다 — 프록시 설정을 맞추기 전에 읽으면 헤더 한 줄로 우회된다.

만료 데이터 정리는 **만료 시각만** 보고 소비 여부를 보지 않는다(ADR 0010). 소비된 리프레시
토큰을 일찍 지우면 재사용 탐지가 무너진다. 만료를 소비보다 먼저 검사하는 규칙(ADR 0008)과
맞물려 있으니 한쪽만 바꾸지 않는다.

테스트에서 호출 제한은 컨텍스트와 수명을 같이 한다. 제한이 관심사가 아닌 테스트는 한도를
크게 열어 두고, 제한을 재는 테스트는 **요청마다 다른 원격 주소**를 준다. 같은 주소를 쓰면
앞선 테스트가 소비한 한도가 넘어와 실행 순서에 따라 결과가 달라진다.

### API 문서

`tests:api-docs` 가 REST Docs 로 만든다. 스니펫은 테스트가, 조립은 asciidoctor 가 한다.
`build` 에 물려 있어 `./gradlew build` 로 항상 최신이 나온다.

**앱은 문서를 서빙하지 않는다.** 서빙하려면 `api:bootJar` 가 이 모듈의 산출물을 가져가야
하는데, 이 모듈은 컨트롤러를 호출하려고 `api` 에 의존하므로 Gradle 순환이 된다.
CI 가 `api-docs` 아티팩트로 올린다.

문서가 **조용히 비어서 나오는** 실패 방식이 있으니 주의한다. `operation::` 매크로가
처리되지 않으면 빌드는 성공하고 HTML 도 만들어지지만 API 내용이 하나도 없다.
`spring-restdocs-asciidoctor` 를 `asciidoctorExt` 에 넣고, AsciidoctorJ 버전을
플러그인 기본값이 아니라 그 확장이 요구하는 값으로 맞춰야 한다. CI 는
`if-no-files-found: error` 로 산출물 자체의 부재를 잡지만, 빈 문서는 잡지 못한다.

### 설계 결정은 `adr/` 에

되돌리는 비용이 큰 결정은 `adr/NNNN-english-kebab-case.md` 에 남긴다. "무엇을 정했는가"보다 **무엇을 포기했고 왜인가**를 적는다. 현재 구조 설명은 `adr/design/` 에 따로 둔다. 번호는 재사용하지 않고, 결정이 뒤집히면 기존 문서의 상태를 `대체됨`으로 바꾸고 새 번호를 발급한다. 색인은 `adr/README.md`.

코드에서 그 근거를 참조할 때는 `(ADR 0002)` 처럼 번호만 적는다. 같은 설명을 여러 파일에 복사하지 않는다.

## 빌드 설정

공통 설정은 `buildSrc` 컨벤션 플러그인에 있고, 모듈 빌드 스크립트는 한 줄로 적용한다.

- `shop.kotlin-library` — 라이브러리 모듈. Kotlin JVM, spring plugin, Boot BOM import, ktlint, JUnit5 설정 포함
- `shop.spring-boot-application` — 위 + `org.springframework.boot` (bootJar). 실행 모듈용

따라서:

- **모듈 의존성에 버전을 적지 않는다.** Spring Boot BOM 이 관리한다.
- BOM 이 관리하지 않는 라이브러리(예: ArchUnit)만 `gradle/libs.versions.toml` 에 넣고 `libs.*` 로 참조한다.
- 플러그인/툴 버전은 `libs.versions.toml` 한 곳에만 둔다. `ktlint-gradle`(플러그인)과 `ktlint`(린터 툴)는 서로 다른 버전이다.
- 컨벤션 플러그인 안에서는 `libs.*` 접근자를 못 쓴다. `VersionCatalogsExtension` 런타임 조회를 쓴다(`shop.kotlin-library.gradle.kts` 참고).
- 새 모듈은 `settings.gradle.kts` 에 include 하고 `shop.kotlin-library` 를 적용한다. 아키텍처 테스트에도 등록한다.

## 설정 파일

각 인프라 모듈이 자기 설정을 `application-*.properties` 로 제공하고, `api` 의 `application.properties` 가 `spring.config.import` 로 모아 불러온다. 새 어댑터 모듈을 만들면 여기에 한 줄 추가한다.

- `application-storage.properties` — H2 인메모리 + `ddl-auto=create-drop`
- `application-mail.properties` — `mail.provider` 로 전송 수단을 고른다(`resend` / `smtp` / `log`). 구현 선택은 `client-mail` 모듈 안의 `@ConditionalOnProperty` 가 하고, `api` 는 어느 것이 떴는지 모른다. Resend 키는 환경변수 `RESEND_API_KEY` 로 주입한다.

## 코드 스타일

ktlint(`ktlint_official`), 최대 120자, 와일드카드 임포트 금지. 규칙은 루트 `.editorconfig` 하나에서 관리하며 IDE 와 ktlint 가 같은 파일을 읽는다. `ktlintCheck` 는 `build` 에 포함된다. 테스트에서는 백틱 서술형 이름을 허용하도록 `property-naming` / `function-naming` 이 꺼져 있다.

## 주석

KDoc(`/** */`)을 쓴다. Javadoc 태그(`@param`, `@return`)는 쓰지 않는다 — Kotlin 은 시그니처가 이미 그 내용을 담고 있어, 태그를 채우면 이름을 한 번 더 적는 일이 된다. 다른 심볼 참조는 마크다운 링크 문법 `[Symbol]` 로 한다.

주석은 **코드가 말하지 못하는 것**만 적는다. 판단 기준은 하나다 — 이 주석을 지웠을 때 독자가 잃는 정보가 있는가.

적을 가치가 있는 것:

- **왜 이렇게 했는가.** 대안을 버린 이유, 제약의 출처(ADR 번호, RFC, 라이브러리 동작). 예) `RawPassword` 의 64자 상한이 bcrypt 의 72바이트 절단 때문이라는 설명.
- **함정.** 코드를 곧이곧대로 읽으면 반대로 이해하게 되는 지점. 예) `existsByUserId` 사전 검사는 동시 요청을 막지 못한다는 사실.
- **경계와 계약.** 포트가 구현체에 요구하는 것, 보안상 담으면 안 되는 값(토큰·평문 비밀번호).

지우는 것:

- **이름을 반복하는 주석.** `/** 토큰이 일치하지 않는다 */ class InvalidVerificationTokenException`, `/** api */ const val API`. 클래스·상수 이름이 이미 같은 말을 한다.
- **코드를 그대로 옮긴 주석.** `"(****)"` 를 돌려주는 `toString()` 위의 "마스킹한다".
- **같은 근거의 복사본.** 하나의 이유는 한 곳에만 적는다. 가장 가까운 곳(대개 도메인 모델이나 포트 인터페이스)을 정본으로 두고, 나머지는 `[Symbol]` 로 가리키거나 아무것도 적지 않는다. 같은 문단이 세 파일에 있으면 셋 다 낡는다.
- **프레임워크 설명.** `@Transactional` 이 트랜잭션을 연다는 식의, 문서를 읽으면 알 수 있는 내용.
- **주석 처리한 죽은 코드.** 지운다. git 이 기억한다.

위치:

- 클래스·public 함수의 설계 의도는 KDoc, 특정 한 줄에 걸린 함정은 그 줄 바로 위 `//`.
- DTO, JPA 엔티티 필드, 어댑터의 단순 위임 메서드에는 달지 않는다. 엔티티는 제약 이름처럼 판별에 쓰이는 값이 있을 때만 클래스 KDoc 을 둔다.
- 테스트는 백틱 서술형 이름이 곧 명세다. 이름에 담기지 않는 배경(이 규칙이 왜 필요한가)만 KDoc 으로 덧붙인다.

## 브랜치와 커밋

GitHub Flow. `main` 에 직접 push 하지 않고 `<type>/<간단한-설명>` 브랜치에서 PR 을 연다. 머지는 Squash and merge.

커밋은 Conventional Commits: `<type>(<scope>): <subject>`. scope 는 모듈 이름(`core-enum`, `core-domain`, `storage-db`, `api`, `build`, `docs`), subject 는 50자 이내 한국어 명령형. 자세한 규칙은 `CONTRIBUTING.md` 참고.
