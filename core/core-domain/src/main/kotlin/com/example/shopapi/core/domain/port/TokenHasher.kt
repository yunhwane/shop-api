package com.example.shopapi.core.domain.port

/**
 * 리프레시 토큰을 저장하고 조회하기 위한 단방향 해시.
 *
 * 비밀번호와 달리 느린 해시를 쓰지 않는다. 리프레시 토큰은 충분한 엔트로피를 가진
 * 난수라 사전 공격 대상이 아니고, 매 재발급마다 조회 키로 쓰인다(ADR 0008).
 */
fun interface TokenHasher {
    fun hash(raw: String): String
}
