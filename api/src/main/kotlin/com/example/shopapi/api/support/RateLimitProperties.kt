package com.example.shopapi.api.support

import com.example.shopapi.core.domain.port.RateLimitPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 남용 방지 정책(ADR 0009).
 *
 * 정책은 api 가 갖고 세는 방법은 어댑터가 갖는다. "무엇을 얼마나 허용할지"는
 * 서비스 판단이고, "어떻게 세는지"는 교체 가능한 구현이다.
 */
@ConfigurationProperties(prefix = "security.rate-limit")
data class RateLimitProperties(
    /** 한 곳에서 여러 주소를 시도하는 열거를 막는다 */
    val verificationPerIp: RateLimitPolicy = RateLimitPolicy(10, Duration.ofHours(1)),
    /** 한 주소로 발송을 반복 요청하는 메일 폭탄을 막는다 */
    val verificationPerEmail: RateLimitPolicy = RateLimitPolicy(3, Duration.ofHours(1)),
    /**
     * 로그인은 IP 단위로만, 실패만 센다.
     *
     * 아이디 단위로 막으면 공격자가 남의 계정을 잠글 수 있고, 성공까지 세면
     * 여러 사람이 한 IP 를 쓰는 환경에서 아무도 공격하지 않았는데 한도가 찬다.
     */
    val loginFailurePerIp: RateLimitPolicy = RateLimitPolicy(20, Duration.ofMinutes(5)),
)
