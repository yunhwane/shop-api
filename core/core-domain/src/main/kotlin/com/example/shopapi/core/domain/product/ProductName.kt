package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 상품명.
 *
 * `UserId` 나 `Email` 과 달리 **대소문자나 공백을 정규화하지 않는다.** 그쪽의 정규화는
 * `Alice` 와 `alice` 가 별개 계정이 되는 것을 막기 위해서였다(ADR 0005). 상품명은
 * 유일성 판단에 쓰이지 않으므로, 정규화하면 판매자가 의도한 표기를 서버가 바꾸는 일만 남는다.
 */
@JvmInline
value class ProductName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 100

        // 줄바꿈과 탭이 이름에 들어가면 목록과 로그의 한 줄 표시가 깨진다.
        private val CONTROL_CHARACTER = Regex("\\p{Cntrl}")

        fun of(raw: String): ProductName {
            val trimmed = raw.trim()
            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
                throw InvalidValueException("name", "${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.")
            }
            if (CONTROL_CHARACTER.containsMatchIn(trimmed)) {
                throw InvalidValueException("name", "줄바꿈이나 제어 문자를 쓸 수 없습니다.")
            }
            return ProductName(trimmed)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: String): ProductName = reconstituting("name") { of(stored) }
    }
}
