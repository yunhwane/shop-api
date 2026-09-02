package com.example.shopapi.client.payment.toss

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * 이 모듈이 자기 설정을 스스로 등록한다. api 는 `@ConfigurationPropertiesScan` 을 켤 필요가 없다.
 */
@Configuration
@EnableConfigurationProperties(TossPaymentProperties::class)
class TossPaymentAdapterConfig {
    /**
     * Toss 승인 호출 전용 [RestClient]. 타임아웃을 명시해야 하는 이유는 `MailAdapterConfig`
     * 와 같다.
     *
     * 연결 자체가 안 될 때(네트워크 단절)를 위한 연결 타임아웃도 별도로 둔다 -
     * [JdkClientHttpRequestFactory] 의 기본 [HttpClient] 는 연결 타임아웃이 없어,
     * `readTimeout` 만으로는 TCP 연결 단계에서 멈춘 요청을 잡지 못한다.
     */
    @Bean
    fun tossRestClient(properties: TossPaymentProperties): RestClient {
        val httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        val requestFactory =
            JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(properties.readTimeout)
            }
        return RestClient.builder().requestFactory(requestFactory).build()
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
