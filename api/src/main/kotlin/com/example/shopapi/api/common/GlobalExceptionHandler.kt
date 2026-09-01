package com.example.shopapi.api.common

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.enums.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.Instant

/**
 * 실패 응답을 RFC 9457 ProblemDetail 로 통일한다(ADR 0006).
 *
 * [ResponseEntityExceptionHandler] 를 상속해 Spring 내장 예외(미지원 메서드, 본문 파싱 실패
 * 등)까지 같은 모양으로 내보낸다. 내장 예외는 이미 ProblemDetail 을 만들어 주므로,
 * [createResponseEntity] 에서 확장 필드만 얹는다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 값 객체 검증 실패. 어느 필드가 왜 틀렸는지를 `errors` 로 알려준다.
     *
     * [InvalidValueException] 은 입력값을 담지 않으므로 비밀번호가 응답에 실릴 일이 없다.
     */
    @ExceptionHandler(InvalidValueException::class)
    fun handleInvalidValue(
        e: InvalidValueException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem =
            ProblemDetails.of(
                errorCode = e.errorCode,
                detail = e.errorCode.defaultMessage,
                instance = request.requestURI,
            )
        problem.setProperty(
            ProblemDetails.ERRORS,
            listOf(ProblemDetails.FieldError(field = e.field, reason = e.reason)),
        )
        return ResponseEntity.status(problem.status).body(problem)
    }

    @ExceptionHandler(DomainException::class)
    fun handleDomain(
        e: DomainException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetails.of(e.errorCode, e.message, request.requestURI)
        // 4xx 는 정상적인 흐름이라 warn 으로 충분하다. 5xx 만 error 로 남긴다.
        if (problem.status >= 500) {
            log.error("도메인 예외. code={}", e.errorCode, e)
        } else {
            log.warn("도메인 예외. code={} detail={}", e.errorCode, e.message)
        }
        return ResponseEntity.status(problem.status).body(problem)
    }

    /**
     * 예상하지 못한 예외.
     *
     * `detail` 에 내부 예외 메시지를 넣지 않는다. 스택트레이스와 원인은 로그로만 남긴다 —
     * 테이블명이나 쿼리가 그대로 클라이언트에 노출되면 공격자에게 지도를 주는 셈이다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        e: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error("처리되지 않은 예외. uri={}", request.requestURI, e)
        val problem =
            ProblemDetails.of(
                errorCode = ErrorCode.INTERNAL_ERROR,
                detail = ErrorCode.INTERNAL_ERROR.defaultMessage,
                instance = request.requestURI,
            )
        return ResponseEntity.status(problem.status).body(problem)
    }

    /**
     * Spring 내장 예외가 만든 ProblemDetail 에도 확장 필드를 얹는다.
     *
     * `code` 는 애플리케이션 오류면 [ErrorCode] 이름, 프로토콜 수준 오류(405, 415 등)면
     * HTTP 상태 이름이다. 어느 쪽이든 `code` 는 항상 존재한다.
     */
    override fun createResponseEntity(
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        if (body is ProblemDetail) {
            body.setProperty(ProblemDetails.CODE, codeFor(statusCode))
            body.setProperty(ProblemDetails.TIMESTAMP, Instant.now())
        }
        return super.createResponseEntity(body, headers, statusCode, request)
    }

    /**
     * `resolve` 를 쓴다. `valueOf` 는 표준이 아닌 상태 코드에서 예외를 던져,
     * 예외 처리 중에 예외가 나는 최악의 경로를 만든다.
     */
    private fun codeFor(statusCode: HttpStatusCode): String =
        when {
            statusCode.value() == HttpStatus.BAD_REQUEST.value() -> ErrorCode.INVALID_REQUEST.name
            else -> HttpStatus.resolve(statusCode.value())?.name ?: "HTTP_${statusCode.value()}"
        }
}
