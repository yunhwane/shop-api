package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.InvalidValueException

/**
 * 해싱된 비밀번호. 도메인은 어떤 알고리즘인지 알지 못한다.
 *
 * 해시도 로그에 남기지 않는다. 유출되면 오프라인 대입 공격의 출발점이 된다.
 */
class EncodedPassword private constructor(
    val value: String,
) {
    override fun toString(): String = "EncodedPassword(****)"

    companion object {
        /** bcrypt 해시는 60자 고정이지만, 알고리즘 교체 여지를 남겨 상한만 둔다 */
        const val MAX_LENGTH = 100

        fun of(raw: String): EncodedPassword {
            if (raw.isBlank() || raw.length > MAX_LENGTH) {
                throw InvalidValueException("password", "인코딩된 비밀번호 형식이 올바르지 않습니다.")
            }
            return EncodedPassword(raw)
        }
    }
}
