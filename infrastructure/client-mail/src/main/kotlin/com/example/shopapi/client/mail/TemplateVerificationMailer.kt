package com.example.shopapi.client.mail

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.Mail
import com.example.shopapi.core.domain.port.MailSender
import com.example.shopapi.core.domain.port.VerificationMailer
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationToken
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * 인증 메일의 제목과 본문을 만들고 [MailSender] 로 넘긴다.
 *
 * 유스케이스가 HTML 과 프론트엔드 URL 을 몰라도 되도록 여기에 가둔다.
 */
@Component
internal class TemplateVerificationMailer(
    private val properties: MailProperties,
    private val mailSender: MailSender,
) : VerificationMailer {
    override fun sendVerification(
        to: Email,
        token: VerificationToken,
    ) {
        mailSender.send(
            Mail(
                to = to,
                subject = SUBJECT,
                html = render(buildLink(token)),
            ),
        )
    }

    /**
     * 링크는 API 가 아니라 프론트엔드 페이지를 가리킨다. 그 페이지가 확인 API 를 호출한다.
     * 메일 클라이언트의 링크 프리페치가 사용자 대신 인증을 눌러버리는 것을 막기 위해서다(ADR 0002).
     */
    private fun buildLink(token: VerificationToken): String =
        UriComponentsBuilder
            .fromUriString(properties.verificationUrl)
            .queryParam("token", token.value)
            .build()
            .toUriString()

    private fun render(link: String): String =
        """
        <div style="font-family:system-ui,-apple-system,sans-serif;max-width:480px;margin:0 auto;padding:32px 24px">
          <h1 style="font-size:20px;margin:0 0 16px">이메일 인증</h1>
          <p style="margin:0 0 24px;color:#444;line-height:1.6">
            아래 버튼을 눌러 이메일 인증을 완료해 주세요.<br>
            인증 후 가입을 이어서 진행할 수 있습니다.
          </p>
          <a href="$link"
             style="display:inline-block;padding:12px 24px;background:#111;color:#fff;
                    text-decoration:none;border-radius:6px;font-weight:600">
            이메일 인증하기
          </a>
          <p style="margin:24px 0 0;color:#888;font-size:13px;line-height:1.6">
            이 링크는 ${EmailVerification.TIME_TO_LIVE.toMinutes()}분 후 만료됩니다.<br>
            직접 요청하지 않으셨다면 이 메일을 무시하셔도 됩니다.
          </p>
        </div>
        """.trimIndent()

    companion object {
        private const val SUBJECT = "[Shop] 이메일 인증을 완료해 주세요"
    }
}
