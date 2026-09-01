package com.example.shopapi.api.auth.dto

import com.example.shopapi.api.auth.application.IssuedTokens
import java.time.Instant

data class LoginRequest(
    val userId: String,
    val password: String,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

/**
 * 재발급 때마다 리프레시 토큰이 바뀐다. 클라이언트는 **새 값을 반드시 저장해야 한다**.
 * 옛 값을 다시 보내면 재사용으로 탐지되어 모든 기기에서 로그아웃된다(ADR 0008).
 */
data class TokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
) {
    companion object {
        fun from(tokens: IssuedTokens): TokenResponse =
            TokenResponse(
                accessToken = tokens.accessToken,
                accessTokenExpiresAt = tokens.accessTokenExpiresAt,
                refreshToken = tokens.refreshToken,
                refreshTokenExpiresAt = tokens.refreshTokenExpiresAt,
            )
    }
}
