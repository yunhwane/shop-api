package com.example.shopapi.core.domain.port

import java.time.Instant

/**
 * 액세스 토큰 발급. 서명 방식과 클레임 구조는 어댑터가 정한다.
 *
 * 읽는 쪽은 [AccessTokenParser] 로 나눠 두었다. 발급은 로그인 유스케이스가,
 * 해석은 인증 필터가 쓴다 - 서로 상대의 능력을 알 필요가 없다.
 */
interface AccessTokenIssuer {
    fun issue(
        userId: Long,
        now: Instant,
    ): AccessToken
}

class AccessToken(
    val value: String,
    val expiresAt: Instant,
) {
    /** 토큰이 로그에 남으면 그것만으로 남의 요청을 보낼 수 있다 */
    override fun toString(): String = "AccessToken(expiresAt=$expiresAt)"
}
