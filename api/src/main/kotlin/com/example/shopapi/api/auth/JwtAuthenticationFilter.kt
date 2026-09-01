package com.example.shopapi.api.auth

import com.example.shopapi.core.domain.port.AccessTokenParser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `Authorization: Bearer` 헤더의 액세스 토큰으로 인증을 채운다.
 *
 * **토큰이 없거나 유효하지 않아도 여기서 예외를 던지지 않는다.** 인증이 필요 없는
 * 엔드포인트도 이 필터를 지나가기 때문이다. 접근 거부는 인가 단계에서 판단한다.
 *
 * 빈으로 등록하지 않는다. Spring Boot 는 `Filter` 타입 빈을 서블릿 컨테이너 필터로도
 * 자동 등록해서, 시큐리티 체인 안과 밖 양쪽에 걸린다. 지금은 [OncePerRequestFilter] 가
 * 두 번째 실행을 건너뛰어 우연히 맞게 동작하지만, 필터 순서를 주거나 체인이 하나 더
 * 생기는 순간 시큐리티 체인 밖에서 채운 인증이 뒤에서 지워지며 조용히 깨진다.
 */
class JwtAuthenticationFilter(
    private val accessTokenParser: AccessTokenParser,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = bearerToken(request)?.let(accessTokenParser::parseUserId)
        if (userId != null && SecurityContextHolder.getContext().authentication == null) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(AuthenticatedUser(userId), null, emptyList())
        }
        filterChain.doFilter(request, response)
    }

    private fun bearerToken(request: HttpServletRequest): String? =
        request
            .getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
