package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.InvalidValueException

/**
 * 인코딩 전 평문 비밀번호.
 *
 * 상한이 [MAX_LENGTH] 이고 ASCII 만 허용하는 이유는 bcrypt 때문이다. bcrypt 는 입력을
 * 72바이트에서 자른다. 한글을 허용하면 UTF-8 로 글자당 3바이트라 64자가 192바이트가 되어
 * **뒷부분이 조용히 버려진다.** 길이가 아니라 바이트 수가 걸리는 문제라
 * "길수록 안전하다"는 직관과 어긋나므로 입력 단계에서 막는다.
 *
 * value class 로 만들지 않는다. 인라인되면 로깅 지점에서 평문 String 이 그대로 찍힌다.
 */
class RawPassword private constructor(
    val value: String,
) {
    override fun toString(): String = "RawPassword(****)"

    companion object {
        const val MIN_LENGTH = 8

        /** bcrypt 의 72바이트 절단 지점 아래로 유지한다 */
        const val MAX_LENGTH = 64

        private val ALLOWED = Regex("^[\\x21-\\x7E]+$")
        private val HAS_LETTER = Regex("[A-Za-z]")
        private val HAS_DIGIT = Regex("[0-9]")

        fun of(raw: String): RawPassword {
            fun reject(reason: String): Nothing = throw InvalidValueException("password", reason)

            if (raw.length !in MIN_LENGTH..MAX_LENGTH) {
                reject("${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.")
            }
            if (!ALLOWED.matches(raw)) {
                reject("공백 없는 영문, 숫자, 특수문자만 쓸 수 있습니다.")
            }
            if (!HAS_LETTER.containsMatchIn(raw) || !HAS_DIGIT.containsMatchIn(raw)) {
                reject("영문과 숫자를 각각 하나 이상 포함해야 합니다.")
            }
            return RawPassword(raw)
        }
    }
}
