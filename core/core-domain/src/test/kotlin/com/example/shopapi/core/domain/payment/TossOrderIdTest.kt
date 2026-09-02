package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TossOrderIdTest {
    @Test
    fun `영숫자와 -, _ 외의 문자를 거부한다`() {
        assertFailsWith<InvalidValueException> { TossOrderId.of("order id!") }
    }

    @Test
    fun `너무 짧거나 길면 거부한다`() {
        assertFailsWith<InvalidValueException> { TossOrderId.of("a".repeat(TossOrderId.MIN_LENGTH - 1)) }
        assertFailsWith<InvalidValueException> { TossOrderId.of("a".repeat(TossOrderId.MAX_LENGTH + 1)) }
    }

    @Test
    fun `허용 범위 안이면 통과한다`() {
        TossOrderId.of("ord-1-abcdefgh")
    }
}
