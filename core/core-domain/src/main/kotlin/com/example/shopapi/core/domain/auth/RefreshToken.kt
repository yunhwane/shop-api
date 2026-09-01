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
     * 재발급에 쓸 수 있는 상태인지 확인한다.
     *
     * **만료를 먼저 본다.** 소비 여부를 앞에 두면 기한이 한참 지난 토큰도 재사용으로
     * 판정되어 폐기를 유발한다. 소비된 행은 지워지지 않고 남으므로, 오래전에 새어 나간
     * 토큰 하나가 언제든 피해자를 전 기기에서 로그아웃시키는 버튼이 된다.
     * 기한이 지난 토큰은 아무것도 할 수 없는 값이므로 그냥 무효로 끝낸다.
     *
     * 상태를 바꾸지 않는다. 소비 처리는 저장소가 원자적으로 한다 - 여기서 읽고 바꿔 쓰면
     * 같은 토큰으로 들어온 동시 요청이 둘 다 통과한다.
     */
    fun ensureUsable(now: Instant) {
        if (now >= expiresAt) throw RefreshTokenExpiredException()
        if (usedAt != null) throw RefreshTokenReusedException(userId)
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
