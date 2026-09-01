package com.example.shopapi.core.domain.verification

import com.example.shopapi.core.domain.common.InvalidValueException

/**
 * 인증 절차의 공개 식별자. 인증을 요청한 클라이언트가 보관한다.
 *
 * 상태 폴링과 가입 요청에 쓰인다. 이 값만으로는 인증을 통과할 수 없다 —
 * 인증을 완료하려면 메일함에 도착한 [VerificationToken] 이 필요하다(ADR 0002).
 */
@JvmInline
value class VerificationId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        fun of(raw: String): VerificationId {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > 36) {
                throw InvalidValueException("verificationId", "인증 식별자 형식이 올바르지 않습니다.")
            }
            return VerificationId(trimmed)
        }
    }
}
