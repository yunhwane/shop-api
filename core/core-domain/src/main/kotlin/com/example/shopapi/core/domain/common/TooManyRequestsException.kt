package com.example.shopapi.core.domain.common

import com.example.shopapi.core.enums.ErrorCode
import java.time.Duration

/**
 * 호출 제한을 넘겼다(ADR 0009).
 *
 * [retryAfter] 는 응답의 `Retry-After` 헤더가 된다. 언제 다시 시도할지 알려 주지 않으면
 * 클라이언트가 계속 두드리게 되고, 그러면 제한이 부하를 줄이지 못한다.
 */
class TooManyRequestsException(
    val retryAfter: Duration,
) : DomainException(ErrorCode.TOO_MANY_REQUESTS)
