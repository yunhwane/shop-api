package com.example.shopapi.client.mail

import com.example.shopapi.core.domain.common.MailSendException
import com.example.shopapi.core.domain.port.Mail
import com.example.shopapi.core.domain.port.MailSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Resend HTTP API 어댑터.
 *
 * 공식 SDK 대신 [RestClient] 로 직접 호출한다. 엔드포인트가 하나뿐이라 SDK 가 주는 값이
 * 적고, Spring Boot BOM 이 관리하지 않는 의존성을 늘리지 않기 위해서다.
 */
@Component
@ConditionalOnProperty(prefix = "mail", name = ["provider"], havingValue = "resend", matchIfMissing = true)
internal class ResendMailSender(
    private val properties: MailProperties,
    // 파라미터 이름이 빈 이름(mailRestClient)과 같아야 한다 - client-payment-toss 가
    // 생기면서 RestClient 빈이 mailRestClient/tossRestClient 둘이 됐다(ADR 0017).
    private val mailRestClient: RestClient,
) : MailSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(mail: Mail) {
        try {
            mailRestClient
                .post()
                .uri(properties.resend.endpoint)
                .header("Authorization", "Bearer ${properties.resend.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    ResendRequest(
                        from = properties.from,
                        to = listOf(mail.to.value),
                        subject = mail.subject,
                        html = mail.html,
                    ),
                ).retrieve()
                .toBodilessEntity()
        } catch (e: RestClientException) {
            // 수신자 주소는 남기되 응답 본문은 남기지 않는다. 자격증명이 섞일 수 있다.
            log.error("Resend 메일 발송 실패. to={}", mail.to.value, e)
            throw MailSendException(e)
        }
    }

    private data class ResendRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val html: String,
    )
}
