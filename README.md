# shop-api

Kotlin + Spring Boot 멀티모듈 커머스 API.

## 요구 사항

- JDK 25 (Gradle 툴체인이 자동으로 잡습니다)
- Gradle Wrapper 사용 — 별도 설치 불필요

## 실행

```bash
./gradlew :api:bootRun     # 애플리케이션 실행
./gradlew build            # 전체 빌드 + 테스트 + 린트
./gradlew ktlintFormat     # 코드 포맷 자동 수정
```

## 모듈 구조

```
shop-api/
├─ buildSrc/                        공통 빌드 설정 (컨벤션 플러그인)
├─ gradle/libs.versions.toml        버전 카탈로그
│
├─ core/
│  ├─ core-enum/                    전 계층 공유 enum / 상수. 의존성 없음
│  └─ core-domain/                  도메인 모델, 도메인 서비스, 포트(인터페이스)
│
├─ infrastructure/
│  └─ storage-db/                   core-domain 포트의 JPA 구현
│
└─ api/                             실행 모듈. controller / dto / 설정
```

### 의존 방향

```
api ──implementation──> core:core-domain ──api──> core:core-enum
 └───runtimeOnly──────> infrastructure:storage-db ──implementation──> core:core-domain
```

- `core:*` 는 프레임워크에 의존하지 않는 순수 Kotlin 모듈입니다.
- `api` 는 `storage-db` 를 `runtimeOnly` 로만 참조합니다. 구현체를 컴파일 타임에
  import 할 수 없고, 빈은 런타임에 주입됩니다.

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

영속성 설정은 `infrastructure/storage-db` 가 `application-storage.properties` 로
제공하고, `api` 의 `application.properties` 에서 `spring.config.import` 로 불러옵니다.
현재는 H2 인메모리 + `ddl-auto=create-drop` 입니다.

## 기여

브랜치 전략(GitHub Flow), 커밋 메시지 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md) 를 참고하세요.
