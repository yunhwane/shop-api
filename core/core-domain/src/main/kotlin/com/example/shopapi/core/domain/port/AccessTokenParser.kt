package com.example.shopapi.core.domain.port

/**
 * 액세스 토큰에서 사용자 식별자를 꺼낸다.
 *
 * 서명이 틀렸거나 기한이 지났으면 예외 대신 null 을 준다. 인증 필터에서 이 둘은
 * 구분할 일이 없고 - 어느 쪽이든 인증되지 않은 요청이다 - 정상 흐름에서 흔히 일어난다.
 */
fun interface AccessTokenParser {
    fun parseUserId(token: String): Long?
}
