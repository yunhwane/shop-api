package com.example.shopapi.core.enums

/**
 * 실패 응답의 기계 판독용 식별자(ADR 0006).
 *
 * HTTP 상태 코드는 여기 두지 않는다. `409` 냐 `400` 이냐는 표현 계층의 관심사이고,
 * 도메인은 "무엇이 잘못됐는가"만 안다. 매핑은 api 모듈의 `ErrorCodeHttpStatus` 가 갖는다.
 */
enum class ErrorCode(
    val defaultMessage: String,
) {
    /** 요청 형식 또는 값 객체 검증 실패. 필드별 사유는 응답의 errors 확장 필드에 담는다 */
    INVALID_REQUEST("요청 값이 올바르지 않습니다."),

    DUPLICATE_USER_ID("이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL("이미 가입된 이메일입니다."),

    VERIFICATION_NOT_FOUND("인증 정보를 찾을 수 없습니다."),
    VERIFICATION_EXPIRED("인증 기한이 지났습니다. 다시 요청해 주세요."),
    VERIFICATION_NOT_COMPLETED("이메일 인증을 먼저 완료해 주세요."),
    VERIFICATION_ALREADY_USED("이미 사용된 인증입니다."),
    INVALID_VERIFICATION_TOKEN("유효하지 않은 인증 토큰입니다."),

    /** 없는 아이디와 틀린 비밀번호를 구분하지 않는다(ADR 0008) */
    INVALID_CREDENTIALS("아이디 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_SUSPENDED("정지된 계정입니다."),
    ACCOUNT_WITHDRAWN("탈퇴한 계정입니다."),

    INVALID_REFRESH_TOKEN("유효하지 않은 리프레시 토큰입니다."),

    /** 이미 소비된 리프레시 토큰이 다시 왔다. 유출 신호로 보고 전부 무효화한다 */
    REFRESH_TOKEN_REUSED("보안을 위해 모든 기기에서 로그아웃했습니다. 다시 로그인해 주세요."),

    UNAUTHENTICATED("로그인이 필요합니다."),
    ACCESS_DENIED("권한이 없습니다."),

    /** 남용 방지 호출 제한을 넘겼다(ADR 0009) */
    TOO_MANY_REQUESTS("요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),

    MAIL_SEND_FAILED("메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR("서버 오류가 발생했습니다."),
}
