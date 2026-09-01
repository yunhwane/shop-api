package com.example.shopapi.client.mail

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 메일 설정.
 *
 * [provider] 로 전송 수단을 고른다. api 모듈은 어느 구현이 떴는지 알지 못한다(ADR 0004).
 */
@ConfigurationProperties(prefix = "mail")
data class MailProperties(
    /** `resend` 또는 `smtp` */
    val provider: String = "resend",
    /** 발신자 주소. 예) `Shop <no-reply@example.com>` */
    val from: String = "Shop <no-reply@example.com>",
    /**
     * 인증 링크가 향할 프론트엔드 페이지. 토큰은 `?token=` 으로 붙는다.
     *
     * API 를 직접 가리키지 않는다. 메일 클라이언트의 링크 프리페치가 사용자 대신
     * 인증을 눌러버리기 때문이다(ADR 0002).
     */
    val verificationUrl: String = "http://localhost:3000/verify",
    /** 메일 게이트웨이 응답 대기 한도. 요청 스레드를 붙잡는 시간이라 짧게 잡는다 */
    val readTimeout: Duration = Duration.ofSeconds(5),
    val resend: Resend = Resend(),
) {
    data class Resend(
        val apiKey: String = "",
        val endpoint: String = "https://api.resend.com/emails",
    )
}
