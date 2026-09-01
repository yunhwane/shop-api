package com.example.shopapi.security

import com.example.shopapi.core.domain.port.IssuedRefreshToken
import com.example.shopapi.core.domain.port.RefreshTokenIssuer
import com.example.shopapi.core.domain.port.TokenGenerator
import com.example.shopapi.core.domain.port.TokenHasher
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 불투명 난수를 리프레시 토큰으로 쓴다. JWT 로 만들지 않는 이유는 ADR 0008 에 있다.
 */
@Component
internal class RandomRefreshTokenIssuer(
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val properties: JwtProperties,
) : RefreshTokenIssuer {
    override fun issue(now: Instant): IssuedRefreshToken {
        val value = tokenGenerator.generate()
        return IssuedRefreshToken(
            value = value,
            hash = tokenHasher.hash(value),
            expiresAt = now + properties.refreshTokenTtl,
        )
    }
}
