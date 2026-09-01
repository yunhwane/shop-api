package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.user.EncodedPassword
import com.example.shopapi.core.domain.user.RawPassword

/**
 * 비밀번호 단방향 해싱.
 *
 * 도메인은 bcrypt 인지 argon2 인지 알지 못한다. 알고리즘 교체가 어댑터 교체로 끝난다(ADR 0004).
 */
interface PasswordEncoder {
    fun encode(raw: RawPassword): EncodedPassword

    fun matches(
        raw: RawPassword,
        encoded: EncodedPassword,
    ): Boolean
}
