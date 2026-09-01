package com.example.shopapi.client.mail

import com.example.shopapi.core.domain.common.MailSendException
import com.example.shopapi.core.domain.port.Mail
import com.example.shopapi.core.domain.port.MailSender
import jakarta.mail.MessagingException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * SMTP 어댑터. Gmail 등 표준 SMTP 서버에 붙일 때 쓴다.
 *
 * 접속 정보는 Spring Boot 의 `spring.mail.*` 프로퍼티를 그대로 따른다.
 */
@Component
@ConditionalOnProperty(prefix = "mail", name = ["provider"], havingValue = "smtp")
internal class SmtpMailSender(
    private val properties: MailProperties,
    private val javaMailSender: JavaMailSender,
) : MailSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(mail: Mail) {
        try {
            val message = javaMailSender.createMimeMessage()
            MimeMessageHelper(message, false, Charsets.UTF_8.name()).apply {
                setFrom(properties.from)
                setTo(mail.to.value)
                setSubject(mail.subject)
                setText(mail.html, true)
            }
            javaMailSender.send(message)
        } catch (e: MailException) {
            log.error("SMTP 메일 발송 실패. to={}", mail.to.value, e)
            throw MailSendException(e)
        } catch (e: MessagingException) {
            // MimeMessageHelper 는 MailException 계열이 아닌 이 예외를 던진다.
            log.error("SMTP 메시지 구성 실패. to={}", mail.to.value, e)
            throw MailSendException(e)
        }
    }
}
