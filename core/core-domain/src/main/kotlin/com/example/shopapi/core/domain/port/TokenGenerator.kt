package com.example.shopapi.core.domain.port

/** 추측 불가능한 임의 문자열. 인증 식별자와 토큰 생성에 쓴다. */
fun interface TokenGenerator {
    fun generate(): String
}
