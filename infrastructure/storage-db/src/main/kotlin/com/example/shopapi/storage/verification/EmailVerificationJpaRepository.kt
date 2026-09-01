package com.example.shopapi.storage.verification

import org.springframework.data.jpa.repository.JpaRepository

internal interface EmailVerificationJpaRepository : JpaRepository<EmailVerificationJpaEntity, Long> {
    fun findByVerificationId(verificationId: String): EmailVerificationJpaEntity?

    fun findByToken(token: String): EmailVerificationJpaEntity?

    fun deleteByEmailAndVerifiedAtIsNull(email: String)
}
