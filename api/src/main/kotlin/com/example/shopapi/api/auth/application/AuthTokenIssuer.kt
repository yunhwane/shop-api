package com.example.shopapi.api.auth.application

import com.example.shopapi.core.domain.auth.RefreshToken
import com.example.shopapi.core.domain.port.AccessTokenIssuer
import com.example.shopapi.core.domain.port.RefreshTokenIssuer
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import org.springframework.stereotype.Component
import java.time.Instant

/** 로그인과 재발급이 같은 방식으로 토큰 쌍을 만들도록 한곳에 모은다. */
@Component
class AuthTokenIssuer(
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenIssuer: RefreshTokenIssuer,
    private val refreshTokens: RefreshTokenRepository,
) {
    fun issueFor(
        userId: Long,
        now: Instant,
    ): IssuedTokens {
        val accessToken = accessTokenIssuer.issue(userId, now)
        val refreshToken = refreshTokenIssuer.issue(now)

        // 저장소에는 해시만 간다. 원문은 응답으로만 나간다(ADR 0008).
        refreshTokens.save(
            RefreshToken.issue(
                userId = userId,
                tokenHash = refreshToken.hash,
                expiresAt = refreshToken.expiresAt,
                now = now,
            ),
        )

        return IssuedTokens(
            accessToken = accessToken.value,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = refreshToken.value,
            refreshTokenExpiresAt = refreshToken.expiresAt,
        )
    }
}

class IssuedTokens(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
) {
    override fun toString(): String = "IssuedTokens(accessTokenExpiresAt=$accessTokenExpiresAt)"
}
