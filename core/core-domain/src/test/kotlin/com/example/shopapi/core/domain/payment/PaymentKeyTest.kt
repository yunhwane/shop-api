package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentKeyTest {
    @Test
    fun `빈 값을 거부한다`() {
        assertFailsWith<InvalidValueException> { PaymentKey.of("   ") }
    }

    @Test
    fun `toString 은 값을 가린다`() {
        assertEquals("PaymentKey(****)", PaymentKey.of("secret-key").toString())
    }
}
