package com.example.shopapi.storage.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

internal interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, Long> {
    /**
     * `used_at IS NULL` 조건을 UPDATE 문에 넣어 DB 가 경합을 판정하게 한다.
     * 영향 행 수가 0 이면 다른 요청이 먼저 소비한 것이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update RefreshTokenJpaEntity t set t.usedAt = :usedAt " +
            "where t.id = :id and t.usedAt is null",
    )
    fun markUsedIfUnused(
        @Param("id") id: Long,
        @Param("usedAt") usedAt: Instant,
    ): Int

    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    fun deleteByTokenHash(tokenHash: String)

    fun deleteAllByUserId(userId: Long)
}
