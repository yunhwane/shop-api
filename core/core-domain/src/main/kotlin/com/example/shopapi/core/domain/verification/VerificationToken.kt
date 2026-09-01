package com.example.shopapi.core.domain.verification

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 인증 메일 링크에만 담기는 비밀 값. 응답 본문에 절대 노출하지 않는다.
 *
 * 이 값을 가로챈 제3자는 인증을 완료시킬 수는 있어도 가입은 하지 못한다.
 * 가입에는 인증을 시작한 클라이언트가 가진 [VerificationId] 가 함께 필요하다(ADR 0002).
 */
@JvmInline
value class VerificationToken private constructor(
    val value: String,
) {
    override fun toString(): String = "VerificationToken(****)"

    companion object {
        fun of(raw: String): VerificationToken {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > 36) {
                throw InvalidValueException("token", "인증 토큰 형식이 올바르지 않습니다.")
            }
            return VerificationToken(trimmed)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: String): VerificationToken = reconstituting("token") { of(stored) }
    }
}
