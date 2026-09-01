package com.example.shopapi.api.common

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.TooManyRequestsException
import com.example.shopapi.core.enums.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
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

    /**
     * 언제 다시 시도할지 알려 준다.
     *
     * `Retry-After` 가 없으면 클라이언트가 간격을 모른 채 계속 두드리고, 그러면 제한이
     * 부하를 줄이지 못한다. 확장 필드로도 함께 담아 헤더를 읽지 않는 클라이언트를 돕는다.
     */
    @ExceptionHandler(TooManyRequestsException::class)
    fun handleTooManyRequests(
        e: TooManyRequestsException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val retryAfterSeconds = e.retryAfter.toSeconds().coerceAtLeast(1)
        val problem = ProblemDetails.of(e.errorCode, e.message, request.requestURI)
        problem.setProperty("retryAfterSeconds", retryAfterSeconds)

        log.warn("호출 제한 초과. uri={}", request.requestURI)
        return ResponseEntity
            .status(problem.status)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(problem)
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
     * 인증되지 않은 요청. Spring Security 의 필터 단계에서 올라온다.
     *
     * 왜 실패했는지(토큰 없음 / 만료 / 서명 오류)는 알려주지 않는다. 공격자에게
     * 다음 시도의 힌트가 되고, 정상 클라이언트는 어느 쪽이든 다시 로그인하면 된다.
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(
        e: AuthenticationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.debug("인증 실패. uri={}", request.requestURI, e)
        val problem =
            ProblemDetails.of(
                errorCode = ErrorCode.UNAUTHENTICATED,
                detail = ErrorCode.UNAUTHENTICATED.defaultMessage,
                instance = request.requestURI,
            )
        return ResponseEntity.status(problem.status).body(problem)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        e: AccessDeniedException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn("접근 거부. uri={}", request.requestURI)
        val problem =
            ProblemDetails.of(
                errorCode = ErrorCode.ACCESS_DENIED,
                detail = ErrorCode.ACCESS_DENIED.defaultMessage,
                instance = request.requestURI,
            )
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
