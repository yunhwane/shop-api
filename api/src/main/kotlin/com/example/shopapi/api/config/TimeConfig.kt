package com.example.shopapi.api.config

import com.example.shopapi.api.support.RateLimitProperties
import com.example.shopapi.core.domain.port.TimeProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

/**
 * 시계는 바깥 기술이 아니라서 별도 인프라 모듈로 빼지 않는다(ADR 0004 의 어댑터 기준).
 * 포트로 둔 이유는 만료 로직 테스트를 결정적으로 만들기 위해서다.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class TimeConfig {
    @Bean
    fun timeProvider(): TimeProvider = TimeProvider { Instant.now() }
}
