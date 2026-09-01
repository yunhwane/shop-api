package com.example.shopapi.api.config

import com.example.shopapi.api.auth.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticationEntryPoint: AuthenticationEntryPoint,
        accessDeniedHandler: AccessDeniedHandler,
    ): SecurityFilterChain =
        http
            // 토큰을 헤더로 받고 쿠키를 쓰지 않으므로 CSRF 공격 경로가 없다.
            // 브라우저가 자동으로 붙여 주는 자격이 하나도 없기 때문이다.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, "/api/v1/users")
                    .permitAll()
                    .requestMatchers("/api/v1/email-verifications/**", "/api/v1/auth/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler)
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    /**
     * 인증·인가 실패도 `GlobalExceptionHandler` 를 거치게 한다.
     *
     * Spring Security 는 필터 단계에서 실패하므로 기본값으로는 컨트롤러 어드바이스를 타지 않고,
     * 빈 본문이나 Spring 기본 형식이 나간다. 그러면 실패 응답 하나만 계약을 벗어난다(ADR 0006).
     * 예외를 그대로 리졸버에 넘겨 나머지와 같은 ProblemDetail 로 만든다.
     */
    @Bean
    fun authenticationEntryPoint(
        @Qualifier("handlerExceptionResolver") resolver: HandlerExceptionResolver,
    ): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, exception ->
            resolver.resolveException(request, response, null, exception)
        }

    @Bean
    fun accessDeniedHandler(
        @Qualifier("handlerExceptionResolver") resolver: HandlerExceptionResolver,
    ): AccessDeniedHandler =
        AccessDeniedHandler { request, response, exception ->
            resolver.resolveException(request, response, null, exception)
        }
}
