package com.example.shopapi.core.domain.common

/**
 * 금액. **원 단위 정수**로 든다(ADR 0012).
 *
 * 부동소수를 쓰지 않는 이유는 자명하지만, `BigDecimal` 을 버린 이유는 덜 자명하다 —
 * `BigDecimal` 의 `equals` 는 소수 자릿수까지 비교해서 `1000` 과 `1000.00` 을 다른 값으로
 * 본다. 값 객체의 동등성이 값이 아니라 표현 방식에 흔들린다는 뜻이다.
 *
 * 통화를 두지 않는다. 나눗셈도 두지 않는다 — 반올림 규칙은 기술이 아니라 정책이라
 * 규칙 없이 열어 두면 부르는 곳마다 다른 답이 나온다.
 */
@JvmInline
value class Money private constructor(
    val amount: Long,
) : Comparable<Money> {
    operator fun plus(other: Money): Money = of(amount + other.amount)

    operator fun times(quantity: Int): Money = of(amount * quantity)

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    override fun toString(): String = amount.toString()

    companion object {
        const val MIN_AMOUNT = 0L

        /** 담을 수 없는 값이라서가 아니라 0 을 하나 더 붙인 입력을 잡기 위한 상한이다 */
        const val MAX_AMOUNT = 1_000_000_000L

        val ZERO = Money(0)

        fun of(amount: Long): Money {
            if (amount !in MIN_AMOUNT..MAX_AMOUNT) {
                throw InvalidValueException("price", "${MIN_AMOUNT}원 이상 ${MAX_AMOUNT}원 이하여야 합니다.")
            }
            return Money(amount)
        }

        /**
         * 저장소에서 읽어온 값을 복원한다. storage 어댑터만 호출한다.
         *
         * 상한을 나중에 **좁히면** 이미 저장된 값이 여기서 걸려 그 상품의 조회가 통째로
         * 500 이 된다. 좁힐 때는 데이터 점검이 먼저다(ADR 0012).
         */
        fun reconstitute(stored: Long): Money = reconstituting("price") { of(stored) }
    }
}
