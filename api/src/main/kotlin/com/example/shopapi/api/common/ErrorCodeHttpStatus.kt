package com.example.shopapi.api.common

import com.example.shopapi.core.enums.ErrorCode
import org.springframework.http.HttpStatus

/**
 * [ErrorCode] 를 HTTP 상태로 옮긴다.
 *
 * 이 표가 api 모듈에 있는 것이 핵심이다. `409` 냐 `400` 이냐는 HTTP 의 관심사라
 * 도메인이 알 이유가 없다(ADR 0006).
 *
 * 항목을 빠뜨리면 조용히 500 으로 나가므로 `ErrorCodeHttpStatusTest` 가 누락을 잡는다.
 */
object ErrorCodeHttpStatus {
    private val STATUSES: Map<ErrorCode, HttpStatus> =
        mapOf(
            ErrorCode.INVALID_REQUEST to HttpStatus.BAD_REQUEST,
            ErrorCode.VERIFICATION_NOT_COMPLETED to HttpStatus.BAD_REQUEST,
            ErrorCode.VERIFICATION_EXPIRED to HttpStatus.BAD_REQUEST,
            ErrorCode.INVALID_VERIFICATION_TOKEN to HttpStatus.BAD_REQUEST,
            ErrorCode.VERIFICATION_NOT_FOUND to HttpStatus.NOT_FOUND,
            ErrorCode.PRODUCT_NOT_FOUND to HttpStatus.NOT_FOUND,
            ErrorCode.INVALID_CREDENTIALS to HttpStatus.UNAUTHORIZED,
            ErrorCode.INVALID_REFRESH_TOKEN to HttpStatus.UNAUTHORIZED,
            ErrorCode.REFRESH_TOKEN_REUSED to HttpStatus.UNAUTHORIZED,
            ErrorCode.UNAUTHENTICATED to HttpStatus.UNAUTHORIZED,
            ErrorCode.ACCOUNT_SUSPENDED to HttpStatus.FORBIDDEN,
            ErrorCode.ACCOUNT_WITHDRAWN to HttpStatus.FORBIDDEN,
            ErrorCode.ACCESS_DENIED to HttpStatus.FORBIDDEN,
            ErrorCode.DUPLICATE_USER_ID to HttpStatus.CONFLICT,
            ErrorCode.DUPLICATE_EMAIL to HttpStatus.CONFLICT,
            ErrorCode.VERIFICATION_ALREADY_USED to HttpStatus.CONFLICT,
            ErrorCode.PRODUCT_NOT_ON_SALE to HttpStatus.CONFLICT,
            ErrorCode.PRODUCT_DISCONTINUED to HttpStatus.CONFLICT,
            ErrorCode.INSUFFICIENT_STOCK to HttpStatus.CONFLICT,
            ErrorCode.ORDER_NOT_FOUND to HttpStatus.NOT_FOUND,
            ErrorCode.ORDER_NOT_CANCELLABLE to HttpStatus.CONFLICT,
            ErrorCode.TOO_MANY_REQUESTS to HttpStatus.TOO_MANY_REQUESTS,
            ErrorCode.MAIL_SEND_FAILED to HttpStatus.BAD_GATEWAY,
            ErrorCode.INTERNAL_ERROR to HttpStatus.INTERNAL_SERVER_ERROR,
        )

    fun of(errorCode: ErrorCode): HttpStatus = STATUSES[errorCode] ?: HttpStatus.INTERNAL_SERVER_ERROR

    /** 매핑 누락 검사용. 프로덕션 경로에서는 쓰지 않는다 */
    fun mappedCodes(): Set<ErrorCode> = STATUSES.keys
}
