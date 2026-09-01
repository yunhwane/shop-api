package com.example.shopapi.core.domain.port

import java.time.Duration
import java.time.Instant

/**
 * 남용 방지 호출 제한(ADR 0009).
 *
 * 검사와 소비가 한 번에 일어나야 한다. "남았는지 보고 나중에 센다"로 나누면 동시에 들어온
 * 요청이 모두 같은 잔량을 보고 전부 통과한다. 한도가 20이어도 500개를 동시에 던지면
 * 500개가 지나가고, 한도가 막으려던 비용도 그대로 발생한다.
 *
 * 그래서 시작 시점에 [tryConsume] 로 먼저 세고, 세지 않아도 될 결과였다면 [refund] 로
 * 되돌린다. 로그인이 실패만 세는 것은 이 방식으로 표현한다.
 */
interface RateLimiter {
    /** 한 번 소비하고 그 결과를 알려준다. 한도를 넘겨도 소비는 기록한다 */
    fun tryConsume(
        key: String,
        policy: RateLimitPolicy,
        now: Instant,
    ): RateLimitResult

    /** 소비한 것을 되돌린다. 창이 이미 바뀌었다면 아무 일도 하지 않는다 */
    fun refund(
        key: String,
        now: Instant,
    )
}

data class RateLimitPolicy(
    val limit: Int,
    val window: Duration,
)

sealed interface RateLimitResult {
    data object Allowed : RateLimitResult

    /** [retryAfter] 는 현재 창이 끝날 때까지 남은 시간이다 */
    data class Rejected(
        val retryAfter: Duration,
    ) : RateLimitResult
}
