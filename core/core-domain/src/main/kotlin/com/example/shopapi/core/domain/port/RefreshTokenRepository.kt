package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.auth.RefreshToken
import java.time.Instant

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /**
     * 아직 소비되지 않았을 때만 소비 처리하고, 성공했는지 알려준다.
     *
     * 조회하고 바꿔 쓰는 방식으로는 같은 토큰으로 들어온 동시 요청이 둘 다 통과한다.
     * 둘 다 `usedAt == null` 을 읽기 때문이다. 그러면 탈취범이 정상 사용자와 같은 순간에
     * 재발급을 치는 것만으로 유출 탐지를 피해 간다 - 이 기능이 막으려던 바로 그 상황이다.
     *
     * 유니크 제약이 중복 가입의 진짜 방어선인 것과 같은 이유로(ADR 0005), 경합은 DB 가 판정한다.
     */
    fun markUsedIfUnused(
        id: Long,
        usedAt: Instant,
    ): Boolean

    /** 로그아웃. 그 기기의 자격만 끊는다 */
    fun deleteByTokenHash(tokenHash: String)

    /** 재사용이 탐지되면 어느 것이 유출됐는지 알 수 없으므로 전부 끊는다 */
    fun deleteAllByUserId(userId: Long)

    /**
     * 기한이 지난 토큰을 지운다. 지운 행 수를 돌려준다.
     *
     * 소비 여부를 보지 않는다. 소비된 행을 회전 직후에 지우면 재사용 탐지가 무너지지만,
     * 만료된 행은 만료 검사가 소비 검사보다 앞서므로 어떤 판단에도 쓰이지 않는다(ADR 0010).
     */
    fun deleteExpiredBefore(now: Instant): Int
}
