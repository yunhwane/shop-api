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
     * 같은 이메일의 **아직 인증되지 않은** 건을 지운다.
     *
     * 재요청 시 이전 링크를 무효화하기 위한 것이다. 남겨 두면 오래된 메일의 링크로도
     * 인증이 되어, 사용자가 어느 메일을 열었는지에 따라 결과가 달라진다.
     *
     * 대상을 미인증 건으로 좁히는 것이 중요하다. 인증까지 마친 건을 함께 지우면,
     * 누구든 그 주소로 발급을 요청하는 것만으로 남의 인증을 무효화할 수 있다.
     * 사용자가 인증 후 재발송을 누르는 것만으로도 자기 인증이 날아간다.
     */
    fun deleteUnverifiedByEmail(email: Email)
}
