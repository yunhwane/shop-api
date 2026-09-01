package com.example.shopapi.core.domain.common

/**
 * 이메일 주소. 항상 소문자로 정규화된다.
 *
 * 로컬 파트는 규격상 대소문자를 구분하지만 실제로 구분하는 메일 서버는 없다시피 하다.
 * 정규화하지 않으면 `A@x.com` 과 `a@x.com` 이 별개 계정이 된다(ADR 0005).
 */
@JvmInline
value class Email private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /** RFC 5321 이 정한 경로 상한 */
        const val MAX_LENGTH = 254

        // RFC 5322 전체를 정규식으로 검증하는 것은 의미가 없다.
        // 실제 오타를 걸러내는 수준으로만 본다. 진짜 검증은 인증 메일이 도착하는지다.
        private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (normalized.isEmpty()) {
                throw InvalidValueException("email", "이메일을 입력해 주세요.")
            }
            if (normalized.length > MAX_LENGTH) {
                throw InvalidValueException("email", "${MAX_LENGTH}자를 넘을 수 없습니다.")
            }
            if (!PATTERN.matches(normalized)) {
                throw InvalidValueException("email", "이메일 형식이 올바르지 않습니다.")
            }
            return Email(normalized)
        }
    }
}
