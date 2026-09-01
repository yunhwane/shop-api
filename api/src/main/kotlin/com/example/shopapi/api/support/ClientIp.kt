package com.example.shopapi.api.support

import jakarta.servlet.http.HttpServletRequest

/**
 * 요청을 보낸 주소.
 *
 * `X-Forwarded-For` 를 읽지 않는다. 그 헤더는 클라이언트가 지어낼 수 있어서,
 * 신뢰할 수 있는 프록시를 앞에 두고 설정을 맞추기 전에 읽으면 제한이 헤더 한 줄로
 * 우회된다. 프록시를 도입할 때 Spring 의 `ForwardedHeaderFilter` 와 함께 다룬다(ADR 0009).
 */
fun HttpServletRequest.clientIp(): String = remoteAddr ?: "unknown"
