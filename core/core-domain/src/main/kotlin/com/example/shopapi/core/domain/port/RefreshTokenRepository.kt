package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.auth.RefreshToken

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /** 로그아웃. 그 기기의 자격만 끊는다 */
    fun deleteByTokenHash(tokenHash: String)

    /** 재사용이 탐지되면 어느 것이 유출됐는지 알 수 없으므로 전부 끊는다 */
    fun deleteAllByUserId(userId: Long)
}
