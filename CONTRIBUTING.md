# 기여 가이드

## 브랜치 전략 — GitHub Flow

`main` 하나만 장수 브랜치로 두고, 나머지는 짧게 살다 사라지는 작업 브랜치입니다.
`develop` / `release` 브랜치는 두지 않습니다.

```
main ──●────────●────────●────────●──▶  항상 배포 가능한 상태
        \      /          \      /
         ●──●─            ●──●──         작업 브랜치 (수명 짧게)
```

### 흐름

1. `main` 에서 작업 브랜치를 딴다.
2. 작게 커밋한다.
3. 작업 브랜치를 push 하고 PR 을 연다. (작업 중이면 Draft PR)
4. 리뷰 + CI 통과 후 `main` 에 머지한다.
5. 머지한 브랜치는 바로 삭제한다.

### 규칙

- `main` 에 직접 push 하지 않는다. 모든 변경은 PR 을 거친다.
- `main` 은 언제든 배포 가능해야 한다. 깨진 코드를 머지하지 않는다.
- 작업 브랜치는 오래 들고 있지 않는다. 길어지면 `main` 을 자주 rebase 한다.
- 머지는 **Squash and merge** 를 기본으로 한다. `main` 히스토리를 PR 단위로 유지한다.

### 브랜치 이름

```
<type>/<간단한-설명>
<type>/<이슈번호>-<간단한-설명>
```

`<type>` 은 아래 커밋 타입과 같은 값을 쓴다. 소문자 + 하이픈.

```
feat/order-cancel
fix/42-jpa-lazy-init
refactor/storage-db-module
```

---

## 커밋 메시지 — Conventional Commits

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 제목 줄

- `<type>` 은 필수. 아래 표에서 고른다.
- `<scope>` 는 선택. 바뀐 모듈 이름을 쓴다: `core-enum`, `core-domain`, `storage-db`, `api`, `build`, `docs`
- `<subject>` 는 50자 이내, 마침표 없이, **명령형 현재시제**로 쓴다.
  - `주문 취소 기능 추가` (O)
  - `주문 취소 기능을 추가했습니다.` (X)

### 타입

| 타입 | 쓰는 경우 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 코드 개선 |
| `perf` | 성능 개선 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서만 변경 |
| `style` | 포맷팅, 세미콜론 등 동작에 영향 없는 변경 |
| `build` | 빌드 스크립트, 의존성, Gradle 설정 |
| `ci` | CI 설정 |
| `chore` | 그 외 잡무 |

### 본문 / 꼬리말

- 본문은 제목과 빈 줄로 구분하고, **무엇을** 보다 **왜** 를 적는다. 한 줄 72자 이내.
- 이슈 연결: `Closes #42`, `Refs #42`
- 호환성이 깨지는 변경은 `BREAKING CHANGE:` 로 시작하는 문단을 남긴다.

### 예시

```
feat(api): 주문 취소 엔드포인트 추가

결제 완료 이전 상태에서만 취소를 허용한다.
그 외 상태는 409 를 반환한다.

Closes #42
```

```
build: 멀티모듈 구조로 전환

core / infrastructure / api 로 분리하고 공통 빌드 설정을
buildSrc 컨벤션 플러그인으로 옮긴다.
```

### 커밋 템플릿

저장소에 `.gitmessage` 를 두었습니다. 아래를 한 번 실행하면 `git commit` 시
템플릿이 뜹니다.

```bash
git config commit.template .gitmessage
```

---

## 모듈 의존 규칙

```
api ──implementation──> core:core-domain ──api──> core:core-enum
 └───runtimeOnly──────> infrastructure:storage-db ──implementation──> core:core-domain
```

- `core:*` 는 Spring / JPA 에 의존하지 않는다.
- `api` 는 `storage-db` 를 `runtimeOnly` 로만 참조한다. 구현체를 컴파일 타임에 import 할 수 없다.
- 새 모듈을 추가하면 `buildSrc` 의 `shop.kotlin-library` 컨벤션을 적용한다.

## 코드 스타일 — ktlint

포맷은 [ktlint](https://pinterest.github.io/ktlint/) 로 강제합니다. 규칙은
저장소 루트의 `.editorconfig` 하나에서 관리하며, IDE 와 ktlint 가 같은 파일을 읽습니다.

```bash
./gradlew ktlintCheck    # 검사만
./gradlew ktlintFormat   # 자동 수정
```

- 코드 스타일은 `ktlint_official`, 최대 줄 길이 120자, 와일드카드 임포트 금지입니다.
- `./gradlew build` 에 `ktlintCheck` 가 포함되어 있습니다. 따로 실행하지 않아도 빌드가 잡아냅니다.
- 대부분의 위반은 `ktlintFormat` 으로 자동 수정됩니다. 와일드카드 임포트처럼
  자동 수정이 안 되는 항목은 직접 고쳐야 합니다.
- 린터 버전은 `gradle/libs.versions.toml` 의 `ktlint` 한 줄로 관리합니다.

## CI

`.github/workflows/ci.yml` 이 `main` 으로 향하는 PR 과 `main` push 에서 돕니다.

| 단계 | 명령 |
|---|---|
| Lint | `./gradlew ktlintCheck` |
| Test | `./gradlew build` |

실패하면 테스트/ktlint 리포트가 아티팩트로 업로드됩니다.

## 아키텍처 테스트

모듈 의존 규칙은 문서가 아니라 [ArchUnit](https://www.archunit.org/) 테스트로 강제합니다.
`tests/architecture` 모듈에 있고 `./gradlew build` 에 포함됩니다.

```bash
./gradlew :tests:architecture:test
```

| 파일 | 검사 내용 |
|---|---|
| `ModuleDependencyTest` | 모듈 경계, 의존 방향, 계층 구조, 순환 의존 |
| `CodingConventionTest` | 필드 주입 금지, Controller/Entity 위치, 네이밍 |

규칙을 추가하려면 위 두 파일에 `@ArchTest val` 을 하나 더 넣으면 됩니다.
패키지 상수는 `Packages.kt` 한 곳에서 관리합니다.

### 지금은 느슨하게 열어둔 것

모듈이 아직 비어 있어 두 군데를 완화해 뒀습니다. **각 모듈에 코드가 채워지면 되돌려야 합니다.**

- `archunit.properties` 의 `archRule.failOnEmptyShould=false`
  → `true` 로 바꾸면 "검사 대상이 0건인 규칙"을 실패로 잡습니다.
- `ModuleDependencyTest` 의 계층 정의가 `optionalLayer`
  → `layer` 로 바꾸면 빈 계층을 실패로 잡습니다.

## 로컬 검증

PR 을 올리기 전에 아래가 통과해야 합니다. CI 와 같은 명령입니다.

```bash
./gradlew build
```
