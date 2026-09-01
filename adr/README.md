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

## 설계 문서

결정이 아니라 "지금 구조가 어떻게 생겼는가"를 적는다. 코드가 바뀌면 같이 고친다.

- [회원 도메인](design/member-domain.md)

## 작성 규칙

- 파일명은 `NNNN-english-kebab-case.md`, 내용은 한국어.
  파일명을 영문으로 두는 이유는 macOS 와 Linux 의 한글 유니코드 정규화(NFD/NFC)가 달라
  git 이 동일 파일을 다른 경로로 인식하는 사고를 피하기 위해서다.
- 번호는 재사용하지 않는다. 결정이 뒤집히면 기존 문서를 지우지 말고
  상태를 `대체됨(→ NNNN)` 으로 바꾸고 새 번호를 발급한다.
- 상태: `제안됨` / `수락됨` / `대체됨` / `폐기됨`
