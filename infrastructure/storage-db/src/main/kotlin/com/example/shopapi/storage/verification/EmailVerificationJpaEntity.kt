package com.example.shopapi.storage.verification

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationToken
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 이메일 인증의 영속성 모델.
 *
 * `email` 에는 유니크 제약이 없다. 재요청과 재가입으로 같은 주소의 이력이 쌓인다.
 * 대신 "이 주소의 미소비 인증"을 자주 조회하므로 복합 인덱스를 둔다.
 */
@Entity
@Table(
    name = "email_verifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = EmailVerificationJpaEntity.UK_VERIFICATION_ID,
            columnNames = ["verification_id"],
        ),
        UniqueConstraint(name = EmailVerificationJpaEntity.UK_TOKEN, columnNames = ["token"]),
    ],
    indexes = [Index(name = "ix_email_verifications_email", columnList = "email, consumed_at")],
)
class EmailVerificationJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "verification_id", nullable = false, length = 36)
    var verificationId: String,
    @Column(name = "token", nullable = false, length = 36)
    var token: String,
    @Column(name = "email", nullable = false, length = 254)
    var email: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toDomain(): EmailVerification =
        EmailVerification(
            id = id,
            verificationId = VerificationId.reconstitute(verificationId),
            token = VerificationToken.reconstitute(token),
            email = Email.reconstitute(email),
            expiresAt = expiresAt,
            verifiedAt = verifiedAt,
            consumedAt = consumedAt,
            createdAt = createdAt,
        )

    companion object {
        const val UK_VERIFICATION_ID = "uk_email_verifications_verification_id"
        const val UK_TOKEN = "uk_email_verifications_token"

        fun from(verification: EmailVerification): EmailVerificationJpaEntity =
            EmailVerificationJpaEntity(
                id = verification.id,
                verificationId = verification.verificationId.value,
                token = verification.token.value,
                email = verification.email.value,
                expiresAt = verification.expiresAt,
                verifiedAt = verification.verifiedAt,
                consumedAt = verification.consumedAt,
                createdAt = verification.createdAt,
            )
    }
}
