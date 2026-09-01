package com.example.shopapi.core.domain.common

import com.example.shopapi.core.enums.ErrorCode

/**
 * 도메인 규칙 위반. 모든 도메인 예외의 뿌리다.
 *
 * [ErrorCode] 만 들고 HTTP 상태는 모른다. 표현 계층으로의 번역은 api 모듈이 한다(ADR 0006).
 */
abstract class DomainException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
) : RuntimeException(message)

/**
 * 값 객체 생성 시 형식 검증에 실패했다.
 *
 * [field] 와 [reason] 은 응답의 `errors` 확장 필드로 전달된다.
 * 입력값 자체는 담지 않는다 — 비밀번호가 그대로 로그와 응답에 실린다.
 */
class InvalidValueException(
    val field: String,
    val reason: String,
) : DomainException(ErrorCode.INVALID_REQUEST, "$field: $reason")
