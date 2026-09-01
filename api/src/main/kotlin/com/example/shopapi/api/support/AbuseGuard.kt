package com.example.shopapi.api.support

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.common.TooManyRequestsException
import com.example.shopapi.core.domain.port.RateLimitPolicy
import com.example.shopapi.core.domain.port.RateLimitResult
import com.example.shopapi.core.domain.port.RateLimiter
import com.example.shopapi.core.domain.port.TimeProvider
import org.springframework.stereotype.Component

/**
 * 남용 방지 검사를 한곳에 모은다(ADR 0009).
 *
 * 열쇠에 용도를 접두어로 붙인다. 붙이지 않으면 같은 IP 의 로그인과 인증 요청이
 * 서로의 한도를 깎는다.
 */
@Component
class AbuseGuard(
    private val rateLimiter: RateLimiter,
    private val properties: RateLimitProperties,
    private val timeProvider: TimeProvider,
) {
    fun guardVerificationRequest(clientIp: String) {
        consume("verify:ip:$clientIp", properties.verificationPerIp)
    }

    fun guardVerificationRequest(email: Email) {
        consume("verify:email:${email.value}", properties.verificationPerEmail)
    }

    /**
     * 로그인 시작 시점. **소비하지 않는다.**
     *
     * 성공할 요청까지 예산을 깎으면 정상 사용자가 자기 로그인으로 한도를 채운다.
     * 시작 시점에 막는 이유는 이미 한도를 넘긴 요청에 bcrypt 비용을 치르지 않기 위해서다.
     */
    fun ensureLoginAllowed(clientIp: String) {
        reject(rateLimiter.check(loginKey(clientIp), properties.loginFailurePerIp, timeProvider.now()))
    }

    fun recordLoginFailure(clientIp: String) {
        rateLimiter.tryConsume(loginKey(clientIp), properties.loginFailurePerIp, timeProvider.now())
    }

    private fun loginKey(clientIp: String) = "login:ip:$clientIp"

    private fun consume(
        key: String,
        policy: RateLimitPolicy,
    ) {
        reject(rateLimiter.tryConsume(key, policy, timeProvider.now()))
    }

    private fun reject(result: RateLimitResult) {
        if (result is RateLimitResult.Rejected) throw TooManyRequestsException(result.retryAfter)
    }
}
