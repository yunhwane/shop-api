package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/** 주문 한 라인에 담는 수량. */
@JvmInline
value class OrderQuantity private constructor(
    val value: Int,
) {
    override fun toString(): String = value.toString()

    companion object {
        const val MIN_VALUE = 1

        /** 담을 수 없는 값이라서가 아니라 한 라인이 재고를 통째로 쓸어가는 것을 막는 상한이다 */
        const val MAX_VALUE = 100

        fun of(value: Int): OrderQuantity {
            if (value !in MIN_VALUE..MAX_VALUE) {
                throw InvalidValueException("quantity", "$MIN_VALUE~$MAX_VALUE 사이여야 합니다.")
            }
            return OrderQuantity(value)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: Int): OrderQuantity = reconstituting("quantity") { of(stored) }
    }
}
