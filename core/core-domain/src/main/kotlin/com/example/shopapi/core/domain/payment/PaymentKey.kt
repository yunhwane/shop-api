package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * Toss 가 승인 성공 시 내려주는 결제 식별자. 이후 취소·조회에 이 값이 필요해 그대로 든다.
 *
 * 비밀번호는 아니지만 남의 결제를 참조할 수 있는 값이라 [toString] 은 로그에 그대로
 * 찍히지 않게 가린다.
 */
@JvmInline
value class PaymentKey private constructor(
    val value: String,
) {
    override fun toString(): String = "PaymentKey(****)"

    companion object {
        const val MAX_LENGTH = 200

        fun of(raw: String): PaymentKey {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) {
                throw InvalidValueException("paymentKey", "결제 키 형식이 올바르지 않습니다.")
            }
            return PaymentKey(trimmed)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: String): PaymentKey = reconstituting("paymentKey") { of(stored) }
    }
}
