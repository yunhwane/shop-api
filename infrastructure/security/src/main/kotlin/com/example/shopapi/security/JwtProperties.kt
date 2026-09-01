package com.example.shopapi.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 토큰 설정(ADR 0008).
 *
 * [secret] 에 기본값을 두지 않는다. 저장소에 개발용 키를 박아 두면 그 값이 그대로
 * 운영까지 흘러간다. 비어 있으면 기동 시 임의로 만들고 경고를 남긴다.
 */
@ConfigurationProperties(prefix = "security.jwt")
data class JwtProperties(
    val secret: String = "",
    /** 짧게 잡는다. 발급된 액세스 토큰은 만료 전까지 무효화되지 않는다 */
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(14),
)
