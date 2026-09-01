package com.example.shopapi.api.verification.dto

import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.enums.EmailVerificationStatus
import java.time.Instant

data class VerificationRequest(
    val email: String,
)

/**
 * 인증 발급 응답.
 *
 * `token` 은 담지 않는다. 그 값은 메일함에만 있어야 한다(ADR 0002).
 */
data class VerificationIssuedResponse(
    val verificationId: String,
    val expiresAt: Instant,
) {
    companion object {
        fun from(verification: EmailVerification): VerificationIssuedResponse =
            VerificationIssuedResponse(
                verificationId = verification.verificationId.value,
                expiresAt = verification.expiresAt,
            )
    }
}

data class VerificationConfirmRequest(
    val token: String,
)

data class VerificationStatusResponse(
    val status: EmailVerificationStatus,
)
