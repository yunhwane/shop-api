package com.example.shopapi.core.domain.port

import java.time.Duration
import java.time.Instant

/**
 * 남용 방지 호출 제한(ADR 0009).
 *
 * 두 메서드가 필요한 이유는 로그인 때문이다. 로그인은 **실패만** 세야 해서, 요청을 시작할 때는
 * [check] 로 한도만 보고(성공할 요청의 예산을 깎지 않는다), 실패한 뒤에 [tryConsume] 로 센다.
 * 시작 시점에 막을 수 있어야 이미 한도를 넘긴 요청에 bcrypt 비용을 치르지 않는다.
 */
interface RateLimiter {
    /**
     * 소비하지 않고 **예산이 남았는지**만 본다.
     *
     * [tryConsume] 과 판정 기준이 다르다. 그쪽은 방금 소비한 호출이 한도 안이었는지를
     * 보고, 이쪽은 다음 호출을 받을 여유가 있는지를 본다. 한도가 3이면 3회까지 허용하고
     * 4회째 요청은 시작 전에 막힌다.
     */
    fun check(
        key: String,
        policy: RateLimitPolicy,
        now: Instant,
    ): RateLimitResult

    /** 한 번 소비하고 그 결과를 알려준다. 한도를 넘겨도 소비는 기록한다 */
    fun tryConsume(
        key: String,
        policy: RateLimitPolicy,
        now: Instant,
    ): RateLimitResult
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
