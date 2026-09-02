package com.example.shopapi.client.payment.toss

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Toss 결제 어댑터 설정.
 *
 * [provider] 로 실제 호출 여부를 고른다. `fake` 는 로컬 개발과 테스트에서 Toss 시크릿 키
 * 없이 결제 흐름을 돌려볼 때 쓴다 - `mail.provider=log` 와 같은 방식이다(ADR 0004).
 */
@ConfigurationProperties(prefix = "payment.toss")
data class TossPaymentProperties(
    /** `toss` 또는 `fake` */
    val provider: String = "toss",
    val secretKey: String = "",
    val confirmEndpoint: String = "https://api.tosspayments.com/v1/payments/confirm",
    /** `{paymentKey}` 자리에 취소할 결제의 키를 채운다(ADR 0018) */
    val cancelEndpoint: String = "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel",
    /** 결제 게이트웨이 응답 대기 한도. 요청 스레드를 붙잡는 시간이라 짧게 잡는다 */
    val readTimeout: Duration = Duration.ofSeconds(5),
)
