package com.example.shopapi.client.mail

import com.example.shopapi.core.domain.port.Mail
import com.example.shopapi.core.domain.port.MailSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 실제로 보내지 않고 로그만 남긴다. `mail.provider=log` 로 켠다.
 *
 * 로컬 개발과 테스트용이다. Resend API 키 없이 가입 흐름 전체를 돌려볼 수 있고,
 * 인증 링크는 로그에서 꺼내 쓴다. 운영 프로파일에서 이 값을 쓰지 않도록 주의한다.
 */
@Component
@ConditionalOnProperty(prefix = "mail", name = ["provider"], havingValue = "log")
internal class LoggingMailSender : MailSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(mail: Mail) {
        log.info("[메일 발송 생략] to={} subject={}\n{}", mail.to.value, mail.subject, mail.html)
    }
}
