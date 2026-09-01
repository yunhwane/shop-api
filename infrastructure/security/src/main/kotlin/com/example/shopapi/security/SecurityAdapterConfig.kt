package com.example.shopapi.security

import com.example.shopapi.core.domain.port.TokenGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

@Configuration
class SecurityAdapterConfig {
    /**
     * `UUID.randomUUID()` 는 내부적으로 `SecureRandom` 을 쓴다. 122비트 난수라
     * 인증 링크 토큰으로 추측이 사실상 불가능하다.
     */
    @Bean
    fun tokenGenerator(): TokenGenerator = TokenGenerator { UUID.randomUUID().toString() }
}
