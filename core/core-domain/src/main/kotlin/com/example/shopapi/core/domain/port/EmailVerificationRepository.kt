package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationToken

interface EmailVerificationRepository {
    fun save(verification: EmailVerification): EmailVerification

    fun findByVerificationId(verificationId: VerificationId): EmailVerification?

    fun findByToken(token: VerificationToken): EmailVerification?

    /**
     * 같은 이메일의 아직 소비되지 않은 인증을 지운다.
     *
     * 재요청 시 이전 링크를 무효화하기 위한 것이다. 남겨 두면 오래된 메일의 링크로도
     * 인증이 되어, 사용자가 어느 메일을 열었는지에 따라 결과가 달라진다.
     */
    fun deleteUnconsumedByEmail(email: Email)
}
