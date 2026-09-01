package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.verification.VerificationToken

/**
 * "이 주소로 인증 메일을 보내라"는 의도만 표현한다.
 *
 * [MailSender] 와 나눈 이유는 관심사가 다르기 때문이다. MailSender 는 *어떻게* 보내는가
 * (전송 수단)이고, 이쪽은 *무엇을* 보내는가(제목·본문·링크 주소)다. 유스케이스가
 * HTML 템플릿과 프론트엔드 URL 을 알 필요가 없도록 둘 다 client-mail 모듈에 가둔다.
 */
interface VerificationMailer {
    fun sendVerification(
        to: Email,
        token: VerificationToken,
    )
}
