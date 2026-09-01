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
     * 로그인 시작 시점. **먼저 소비한다.**
     *
     * "남았는지 보고 실패하면 센다"로 나누면 동시에 들어온 요청이 모두 같은 잔량을 보고
     * 전부 통과한다. 한도가 20이어도 500개를 동시에 던지면 500번의 bcrypt 가 돌아,
     * 한도가 막으려던 비용이 그대로 발생한다.
     *
     * 성공한 로그인은 [forgiveLogin] 으로 되돌린다. 결과적으로 실패만 세이면서
     * 검사와 소비가 한 번에 일어난다.
     */
    fun guardLoginAttempt(clientIp: String) {
        reject(rateLimiter.tryConsume(loginKey(clientIp), properties.loginFailurePerIp, timeProvider.now()))
    }

    /** 성공한 로그인이 예산을 깎지 않도록 되돌린다 */
    fun forgiveLogin(clientIp: String) {
        rateLimiter.refund(loginKey(clientIp), timeProvider.now())
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
