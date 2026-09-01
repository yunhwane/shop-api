package com.example.shopapi.core.domain.port

import java.time.Instant

/**
 * 리프레시 토큰 원문을 만든다.
 *
 * 발급 자체를 포트로 올린 이유는 수명과 해싱 방식이 어댑터의 관심사이기 때문이다.
 * 유스케이스는 "만들어 달라"고만 하고, 며칠짜리인지도 어떻게 해싱하는지도 모른다.
 */
interface RefreshTokenIssuer {
    fun issue(now: Instant): IssuedRefreshToken
}

/**
 * [value] 는 클라이언트에게만, [hash] 는 저장소에만 간다. 둘을 바꿔 쓰면
 * 저장소 유출이 곧 로그인이 된다(ADR 0008).
 */
class IssuedRefreshToken(
    val value: String,
    val hash: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "IssuedRefreshToken(expiresAt=$expiresAt)"
}
