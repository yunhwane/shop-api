package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 결제 시도 하나를 가리키는, Toss 에 넘기는 식별자.
 *
 * `Order.id` 를 그대로 쓰지 않는다. Toss 는 결제 시도마다 새 값을 요구해서, 실패 후
 * 재시도가 같은 값을 다시 보내면 혼선을 일으킨다(ADR 0017). 형식은 Toss 가 요구하는
 * 영숫자·`-`·`_` 6~64자다.
 */
@JvmInline
value class TossOrderId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 6
        const val MAX_LENGTH = 64

        private val PATTERN = Regex("^[A-Za-z0-9_-]{$MIN_LENGTH,$MAX_LENGTH}$")

        fun of(raw: String): TossOrderId {
            val trimmed = raw.trim()
            if (!PATTERN.matches(trimmed)) {
                throw InvalidValueException("tossOrderId", "영숫자와 -, _ 로 ${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.")
            }
            return TossOrderId(trimmed)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: String): TossOrderId = reconstituting("tossOrderId") { of(stored) }
    }
}
