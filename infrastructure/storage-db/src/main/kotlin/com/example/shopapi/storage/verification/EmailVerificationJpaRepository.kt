package com.example.shopapi.storage.verification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

internal interface EmailVerificationJpaRepository : JpaRepository<EmailVerificationJpaEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailVerificationJpaEntity v where v.expiresAt < :now")
    fun deleteExpiredBefore(
        @Param("now") now: Instant,
    ): Int

    fun findByVerificationId(verificationId: String): EmailVerificationJpaEntity?

    fun findByToken(token: String): EmailVerificationJpaEntity?

    fun deleteByEmailAndVerifiedAtIsNull(email: String)
}
