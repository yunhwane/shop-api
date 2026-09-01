# shop-api

Kotlin + Spring Boot 멀티모듈 커머스 API.

## 요구 사항

- **JDK 25 를 로컬에 설치해야 합니다.** `jvmToolchain(25)` 를 쓰지만 foojay toolchain
  resolver 를 두지 않아, Gradle 이 툴체인을 대신 내려받지 않습니다.
- Gradle 자체는 Wrapper 를 쓰므로 별도 설치가 필요 없습니다.

macOS 에서 JDK 가 없다면:

```bash
brew install openjdk@25
export JAVA_HOME=/opt/homebrew/opt/openjdk@25   # keg-only 라 PATH 에 잡히지 않습니다
```

## 실행

```bash
./gradlew :api:bootRun     # 애플리케이션 실행 (H2 인메모리)
./gradlew build            # 전체 빌드 + 테스트 + 린트
./gradlew ktlintFormat     # 코드 포맷 자동 수정

./gradlew :tests:architecture:test   # 아키텍처 규칙만 검증
```

메일 발송에는 Resend API 키가 필요합니다. 키 없이 띄우려면 발송을 로그로 대체할 수
있습니다. 인증 링크가 애플리케이션 로그에 그대로 출력됩니다.

```bash
./gradlew :api:bootRun --args='--mail.provider=log'
```

## 모듈 구조

```
shop-api/
├─ buildSrc/                        공통 빌드 설정 (컨벤션 플러그인)
├─ gradle/libs.versions.toml        버전 카탈로그
├─ adr/                             아키텍처 결정 기록과 설계 문서
│
├─ core/
│  ├─ core-enum/                    전 계층 공유 enum / 상수. 의존성 없음
│  └─ core-domain/                  도메인 모델, 값 객체, 포트(인터페이스)
│
├─ infrastructure/
│  ├─ storage-db/                   포트의 JPA 구현
│  ├─ security/                     비밀번호 해싱(bcrypt), 토큰 생성
│  └─ client-mail/                  메일 발송(Resend / SMTP)과 템플릿
│
├─ api/                             실행 모듈. controller / dto / 유스케이스 / 설정
│
└─ tests/
   └─ architecture/                 ArchUnit 아키텍처 규칙 검증 (테스트 전용)
```

`tests/*` 는 프로덕션 코드가 없는 검증 전용 모듈입니다. 이 모듈들만 예외적으로
전 모듈을 참조하며, 반대로 이들을 참조하는 곳은 없습니다.

### 의존 방향

```
api ──implementation──> core:core-domain ──api──> core:core-enum
 └───runtimeOnly──────> infrastructure:{storage-db, security, client-mail}
                             └──implementation──> core:core-domain
```

- `core:*` 는 프레임워크에 의존하지 않는 순수 Kotlin 모듈입니다.
- `api` 는 인프라 모듈을 `runtimeOnly` 로만 참조합니다. 구현체를 컴파일 타임에
  import 할 수 없고, 빈은 컴포넌트 스캔으로 런타임에 주입됩니다. 덕분에 구현 교체가
  해당 인프라 모듈 안에서 끝납니다.
- 바깥으로 나가는 모든 호출은 `core-domain` 의 포트를 거칩니다. 저장소든 메일이든
  시계든 마찬가지입니다.

### 빌드 컨벤션

공통 설정은 `buildSrc` 의 컨벤션 플러그인에 있습니다. 각 모듈은 한 줄로 적용합니다.

```kotlin
plugins {
    id("shop.kotlin-library")            // 라이브러리 모듈
    // id("shop.spring-boot-application") // 실행 모듈 (bootJar)
}
```

의존성 버전은 Spring Boot BOM 이 관리하므로 모듈에서는 버전을 적지 않습니다.
플러그인 버전은 `gradle/libs.versions.toml` 한 곳에만 있습니다.

## 설정

각 인프라 모듈이 자기 설정을 `application-*.properties` 로 제공하고, `api` 의
`application.properties` 가 `spring.config.import` 로 모아 불러옵니다.

| 파일 | 제공 모듈 | 내용 |
|---|---|---|
| `application-storage.properties` | `storage-db` | H2 인메모리 + `ddl-auto=create-drop` |
| `application-mail.properties` | `client-mail` | `mail.provider` (`resend` / `smtp` / `log`) |

Resend API 키는 환경변수 `RESEND_API_KEY` 로 주입합니다.

## 설계 문서

되돌리는 비용이 큰 결정은 [`adr/`](adr/) 에 기록합니다. 무엇을 정했는지보다
**무엇을 포기했고 왜인지**를 남깁니다. 색인은 [adr/README.md](adr/README.md) 에 있습니다.

현재 구현된 도메인의 전체 흐름은 [회원 도메인 설계](adr/design/member-domain.md) 를
참고하세요.

## 기여

브랜치 전략(GitHub Flow), 커밋 메시지 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md) 를 참고하세요.
