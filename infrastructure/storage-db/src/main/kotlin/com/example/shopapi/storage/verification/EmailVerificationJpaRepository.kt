package com.example.shopapi.storage.verification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

internal interface EmailVerificationJpaRepository : JpaRepository<EmailVerificationJpaEntity, Long> {
    /**
     * 인증하지 않은 건은 링크 기한으로, 인증한 건은 소비 기한으로 판정한다.
     * [verifiedBefore] 는 `now - CONSUME_TIME_TO_LIVE` 다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from EmailVerificationJpaEntity v " +
            "where (v.verifiedAt is null and v.expiresAt < :now) " +
            "or (v.verifiedAt is not null and v.verifiedAt < :verifiedBefore)",
    )
    fun deleteUnusable(
        @Param("now") now: Instant,
        @Param("verifiedBefore") verifiedBefore: Instant,
    ): Int

    fun findByVerificationId(verificationId: String): EmailVerificationJpaEntity?

    fun findByToken(token: String): EmailVerificationJpaEntity?

    fun deleteByEmailAndVerifiedAtIsNull(email: String)
}
