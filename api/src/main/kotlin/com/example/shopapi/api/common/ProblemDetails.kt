package com.example.shopapi.api.common

import com.example.shopapi.core.enums.ErrorCode
import org.springframework.http.ProblemDetail
import java.net.URI
import java.time.Instant

/**
 * RFC 9457 ProblemDetail 생성 규칙을 한곳에 모은다(ADR 0006).
 *
 * 확장 필드는 둘이다.
 * - `code` : 클라이언트가 분기할 안정적인 심볼
 * - `timestamp` : 로그와 대조할 시각
 */
object ProblemDetails {
    const val CODE = "code"
    const val TIMESTAMP = "timestamp"
    const val ERRORS = "errors"

    fun of(
        errorCode: ErrorCode,
        detail: String,
        instance: String,
    ): ProblemDetail =
        ProblemDetail.forStatus(ErrorCodeHttpStatus.of(errorCode)).apply {
            type = typeUri(errorCode)
            title = errorCode.defaultMessage
            this.detail = detail
            this.instance = URI.create(instance)
            setProperty(CODE, errorCode.name)
            setProperty(TIMESTAMP, Instant.now())
        }

    /** `DUPLICATE_USER_ID` → `/problems/duplicate-user-id` */
    fun typeUri(errorCode: ErrorCode): URI = URI.create("/problems/${errorCode.name.lowercase().replace('_', '-')}")

    /** 검증 실패의 필드별 사유. 입력값 자체는 담지 않는다 */
    data class FieldError(
        val field: String,
        val reason: String,
    )
}
