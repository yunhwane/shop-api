package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationToken
import java.time.Instant

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

    /**
     * **더 이상 쓸 수 없게 된** 인증을 지운다. 지운 행 수를 돌려준다(ADR 0010).
     *
     * `expiresAt` 하나로 판정하면 안 된다. 인증에는 기한이 둘이고, 인증을 마친 건은
     * 링크 기한이 지난 뒤에도 `verifiedAt + CONSUME_TIME_TO_LIVE` 까지 가입에 쓸 수 있다.
     * 링크 기한만 보면 **아직 가입할 수 있는 사용자의 인증이 지워진다.**
     */
    fun deleteUnusableAsOf(now: Instant): Int
}
