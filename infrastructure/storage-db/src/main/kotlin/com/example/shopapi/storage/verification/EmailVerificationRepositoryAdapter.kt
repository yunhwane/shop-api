package com.example.shopapi.storage.verification

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationToken
import org.springframework.stereotype.Repository

@Repository
internal class EmailVerificationRepositoryAdapter(
    private val jpaRepository: EmailVerificationJpaRepository,
) : EmailVerificationRepository {
    override fun save(verification: EmailVerification): EmailVerification =
        jpaRepository.saveAndFlush(EmailVerificationJpaEntity.from(verification)).toDomain()

    override fun findByVerificationId(verificationId: VerificationId): EmailVerification? =
        jpaRepository.findByVerificationId(verificationId.value)?.toDomain()

    override fun findByToken(token: VerificationToken): EmailVerification? =
        jpaRepository.findByToken(token.value)?.toDomain()

    override fun deleteUnconsumedByEmail(email: Email) {
        jpaRepository.deleteByEmailAndConsumedAtIsNull(email.value)
    }
}
