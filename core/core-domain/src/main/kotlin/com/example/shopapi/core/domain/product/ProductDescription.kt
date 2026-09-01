package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 상품 설명. 빈 값을 허용한다.
 *
 * `null` 을 쓰지 않는다. "설명이 없다"와 "설명이 빈 문자열이다"를 구분할 이유가 없는데,
 * 구분되는 순간 이 값을 읽는 모든 코드가 두 경우를 다뤄야 한다.
 *
 * [ProductName] 과 달리 줄바꿈을 허용한다. 설명은 여러 문단으로 쓰인다.
 */
@JvmInline
value class ProductDescription private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 2000

        val EMPTY = ProductDescription("")

        fun of(raw: String): ProductDescription {
            val trimmed = raw.trim()
            if (trimmed.length > MAX_LENGTH) {
                throw InvalidValueException("description", "${MAX_LENGTH}자를 넘을 수 없습니다.")
            }
            return ProductDescription(trimmed)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: String): ProductDescription = reconstituting("description") { of(stored) }
    }
}
