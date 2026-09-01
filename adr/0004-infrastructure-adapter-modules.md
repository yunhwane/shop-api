# 0004. 메일과 암호화 어댑터를 별도 인프라 모듈로 분리한다

- 상태: 수락됨
- 날짜: 2026-09-01
- 관련: [0003](0003-application-layer-placement.md)

## 맥락

회원가입에 두 개의 바깥 기술이 필요하다.

- **메일 발송**: 우선 Resend 를 쓰지만 Gmail/SMTP 등으로 갈아탈 수 있어야 한다.
- **비밀번호 해싱**: bcrypt. Spring Security Crypto 를 쓰려는데,
  `core` 는 Spring 에 의존할 수 없으므로 `core-domain` 안에서 직접 쓸 수 없다.

둘 다 포트는 `core-domain` 에 두는 게 확정이고, 남은 문제는 **구현체를 어디에 두는가**다.

## 결정

인프라 모듈 두 개를 신설한다.

```
infrastructure/
  storage-db/     (기존)  com.example.shopapi.storage
  client-mail/    (신설)  com.example.shopapi.client.mail    MailSender 구현
  security/       (신설)  com.example.shopapi.security       PasswordEncoder 구현
```

세 모듈 모두 `api` 에서 **`runtimeOnly`** 로만 참조한다. `storage-db` 와 동일한 취급이다.

## 근거

- `ShopApiApplication` 이 `com.example.shopapi` 루트에 있어 컴포넌트 스캔이 하위 인프라
  패키지를 모두 훑는다. 각 모듈이 자기 `@Configuration` 으로 빈을 등록하면
  `api` 는 구현체를 컴파일 타임에 보지 않고도 주입받는다. 이미 검증된 배선 방식이다.
- `security` 모듈은 클래스 한두 개뿐이라 과해 보이지만, **`api` 가 어떤 해싱을 쓰는지
  모르게 만드는 값**이 그 비용보다 크다. bcrypt → argon2 교체가 모듈 교체로 끝난다.
  `api` 에 두면 실행 모듈이 암호화 라이브러리를 직접 알게 되고, 나중에 뽑아낼 때
  `api` 코드를 고쳐야 한다.
- 메일은 외부 HTTP API 호출이다. 재시도·타임아웃·자격증명 같은 관심사가 따라붙으므로
  실행 모듈에 섞이면 금방 지저분해진다.

## 결과

**얻는 것**

- 구현 교체가 모듈 안에서 끝난다. `api` 와 `core` 는 손대지 않는다.
- 각 어댑터의 설정 파일을 모듈이 스스로 들고 온다
  (`storage-db` 의 `application-storage.properties` 와 같은 방식).

**치르는 비용**

- 모듈이 4개에서 6개가 된다. `settings.gradle.kts`, `Packages.kt`,
  `ModuleDependencyTest` 의 계층 정의, `tests/architecture` 의 의존성에 각각 등록해야 한다.
- 클래스 몇 개짜리 모듈이 생긴다. 감수한다.

## 메일 구현 교체 방식

`mail.provider` 프로퍼티와 `@ConditionalOnProperty` 로 `client-mail` 모듈 **안에서** 고른다.
`api` 는 어떤 구현이 떴는지 알지 못한다.

```properties
mail.provider=resend   # 또는 smtp
```

`MailSender` 포트는 전송 수단에 중립인 형태여야 한다. Resend 의 응답 타입이나
SMTP 의 세션 개념이 포트 시그니처에 새어 나오면 추상화가 깨진다.

## 아키텍처 테스트에 반영할 것

- `Packages.kt`: `CLIENT = "$ROOT.client.."`, `SECURITY = "$ROOT.security.."` 추가
- `ModuleDependencyTest`
  - 계층 정의에 두 모듈 등록, `mayNotBeAccessedByAnyLayer`
  - `api 는 영속성 구현체를 직접 참조하지 않는다` → 세 인프라 모듈 전체로 확장
  - `infrastructure 는 api 를 모른다` → 동일하게 확장
- 코드가 채워지므로 임시 완화를 되돌린다.
  - `archunit.properties` 의 `archRule.failOnEmptyShould` → `true`
  - `계층 구조를 지킨다` 의 `optionalLayer` → `layer`
