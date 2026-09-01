## 요약

<!-- 이 PR 이 무엇을 하는지 2~3줄로. -->

## 배경 / 이유

<!-- 왜 이 변경이 필요한지. 이슈가 있으면 링크만 남겨도 됩니다. -->

## 변경 내용

<!-- 리뷰어가 훑어볼 수 있게 항목으로. -->

-
-

## 영향 범위

<!-- 해당하는 모듈에 체크. 모듈을 새로 만들었다면 이 목록에도 추가해 주세요. -->

- [ ] `core:core-enum`
- [ ] `core:core-domain`
- [ ] `infrastructure:storage-db`
- [ ] `infrastructure:security`
- [ ] `infrastructure:client-mail`
- [ ] `api`
- [ ] `tests:architecture`
- [ ] `tests:api-docs`
- [ ] 빌드 설정 (`buildSrc`, `settings.gradle.kts`, `gradle/libs.versions.toml`)
- [ ] 문서 (`adr/`, `README.md`, `CLAUDE.md`)

## 확인 방법

<!-- 리뷰어가 어떻게 검증할 수 있는지. 테스트 코드로 충분하면 그렇게 적어주세요. -->

```bash
./gradlew build
```

## 체크리스트

- [ ] `./gradlew build` 가 로컬에서 통과한다
- [ ] 모듈 의존 방향을 지켰다 (`core` 는 Spring/JPA 에 의존하지 않는다)
- [ ] 커밋 메시지가 Conventional Commits 형식이다
- [ ] 필요한 문서를 갱신했다

## 관련 이슈

<!-- Closes #  /  Refs # -->
