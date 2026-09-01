package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 로그인 아이디. 영문과 숫자만 허용하고 소문자로 정규화한다.
 *
 * 정규화하지 않으면 `Alice` 와 `alice` 가 별개 계정이 된다. 사용자가 자기 아이디를
 * 헷갈리고, 시각적으로 유사한 아이디를 사칭에 쓸 수 있다(ADR 0005).
 */
@JvmInline
value class UserId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 20

        private val PATTERN = Regex("^[A-Za-z0-9]{$MIN_LENGTH,$MAX_LENGTH}$")

        fun of(raw: String): UserId {
            val trimmed = raw.trim()
            if (!PATTERN.matches(trimmed)) {
                throw InvalidValueException(
                    field = "userId",
                    reason = "영문과 숫자로 ${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.",
                )
            }
            return UserId(trimmed.lowercase())
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: String): UserId = reconstituting("userId") { of(stored) }
    }
}
