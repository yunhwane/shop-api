package com.example.shopapi.core.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `0원을 허용한다`() {
        assertEquals(0, Money.of(0).amount)
    }

    @Test
    fun `음수를 거부한다`() {
        assertFailsWith<InvalidValueException> { Money.of(-1) }
    }

    @Test
    fun `상한을 넘으면 거부한다`() {
        Money.of(Money.MAX_AMOUNT)

        assertFailsWith<InvalidValueException> { Money.of(Money.MAX_AMOUNT + 1) }
    }

    @Test
    fun `같은 금액은 같은 값이다`() {
        assertEquals(Money.of(1000), Money.of(1000))
    }

    @Test
    fun `금액끼리 비교한다`() {
        assertEquals(true, Money.of(1000) < Money.of(2000))
    }

    /** 연산 결과도 값 객체의 범위를 벗어날 수 없다. 넘으면 조용히 이상한 금액이 남는다. */
    @Test
    fun `연산 결과가 상한을 넘으면 거부한다`() {
        val half = Money.of(Money.MAX_AMOUNT / 2 + 1)

        assertFailsWith<InvalidValueException> { half + half }
        assertFailsWith<InvalidValueException> { half * 2 }
    }

    @Test
    fun `수량만큼 곱한다`() {
        assertEquals(Money.of(30_000), Money.of(10_000) * 3)
    }

    @Test
    fun `저장된 값이 규칙을 어기면 서버 오류로 다룬다`() {
        assertFailsWith<CorruptedDataException> { Money.reconstitute(-1) }
    }
}
