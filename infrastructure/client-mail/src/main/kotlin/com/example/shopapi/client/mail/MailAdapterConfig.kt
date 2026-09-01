package com.example.shopapi.client.mail

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * 이 모듈이 자기 설정을 스스로 등록한다. api 는 `@ConfigurationPropertiesScan` 을 켤 필요가 없다.
 */
@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailAdapterConfig {
    /**
     * 메일 발송 전용 [RestClient].
     *
     * Boot 의 자동 설정 빌더에 기대지 않고 직접 만든다. 타임아웃을 명시해야 하기 때문이다 -
     * 메일 발송은 요청 스레드에서 동기로 일어나므로(설계 문서 6.1), 응답 없는 게이트웨이가
     * 그대로 사용자 요청을 붙잡는다.
     */
    @Bean
    fun mailRestClient(properties: MailProperties): RestClient {
        val requestFactory =
            JdkClientHttpRequestFactory().apply {
                setReadTimeout(properties.readTimeout)
            }
        return RestClient.builder().requestFactory(requestFactory).build()
    }
}
