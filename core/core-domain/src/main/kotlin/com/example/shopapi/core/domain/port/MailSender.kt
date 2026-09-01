package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.common.Email

/**
 * 메일 전송 수단. Resend / SMTP 등 구현을 갈아끼우는 지점이다(ADR 0004).
 *
 * 시그니처에 전송 수단의 개념이 새어 나오면 안 된다. Resend 의 응답 타입이나
 * SMTP 세션이 여기 등장하는 순간 추상화가 깨진다.
 */
interface MailSender {
    fun send(mail: Mail)
}

class Mail(
    val to: Email,
    val subject: String,
    val html: String,
)
