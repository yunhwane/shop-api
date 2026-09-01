package com.example.shopapi.core.domain.auth

import java.time.Instant

/**
 * 액세스 토큰을 다시 받기 위한 자격. 재발급 때마다 회전한다(ADR 0008).
 *
 * 원문 대신 해시를 들고 다닌다. DB 가 유출되어도 저장된 값으로 바로 로그인할 수 없어야 한다.
 */
class RefreshToken(
    val id: Long?,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val createdAt: Instant,
) {
    /**
     * 재발급에 사용한다. 한 번만 성공한다.
     *
     * 이미 소비된 토큰이 다시 오는 것은 정상 흐름에서 일어나지 않는다. 유출로 보고
     * [RefreshTokenReusedException] 을 던지면, 호출자가 해당 사용자의 토큰을 전부 지운다.
     */
    fun use(now: Instant): RefreshToken =
        when {
            usedAt != null -> {
                throw RefreshTokenReusedException(userId)
            }

            now >= expiresAt -> {
                throw RefreshTokenExpiredException()
            }

            else -> {
                RefreshToken(
                    id = id,
                    userId = userId,
                    tokenHash = tokenHash,
                    expiresAt = expiresAt,
                    usedAt = now,
                    createdAt = createdAt,
                )
            }
        }

    /** 해시가 로그에 남지 않게 한다. 조회 키라 그것만으로 재발급이 가능하다 */
    override fun toString(): String = "RefreshToken(id=$id, userId=$userId, expiresAt=$expiresAt)"

    companion object {
        fun issue(
            userId: Long,
            tokenHash: String,
            expiresAt: Instant,
            now: Instant,
        ): RefreshToken =
            RefreshToken(
                id = null,
                userId = userId,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
                usedAt = null,
                createdAt = now,
            )
    }
}
