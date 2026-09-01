package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 재고 수량.
 *
 * 음수를 담을 수 없다는 것이 이 타입의 존재 이유지만, **실제 차감 경로는 이 타입을
 * 거치지 않는다.** 차감은 `ProductRepository.decreaseStockIfEnough` 의 조건부 원자
 * 갱신이 하고, 음수 방지는 그 SQL 의 `WHERE` 절이 맡는다(ADR 0014).
 */
@JvmInline
value class StockQuantity private constructor(
    val value: Int,
) {
    val isZero: Boolean
        get() = value == 0

    fun isAtLeast(quantity: Int): Boolean = value >= quantity

    override fun toString(): String = value.toString()

    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 1_000_000

        val ZERO = StockQuantity(0)

        fun of(value: Int): StockQuantity {
            if (value !in MIN_VALUE..MAX_VALUE) {
                throw InvalidValueException("stockQuantity", "$MIN_VALUE~$MAX_VALUE 사이여야 합니다.")
            }
            return StockQuantity(value)
        }

        /** 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다 */
        fun reconstitute(stored: Int): StockQuantity = reconstituting("stockQuantity") { of(stored) }
    }
}
