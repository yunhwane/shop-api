package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OrderQuantityTest {
    @Test
    fun `0 이하를 거부한다`() {
        assertFailsWith<InvalidValueException> { OrderQuantity.of(0) }
        assertFailsWith<InvalidValueException> { OrderQuantity.of(-1) }
    }

    @Test
    fun `상한을 넘으면 거부한다`() {
        OrderQuantity.of(OrderQuantity.MAX_VALUE)

        assertFailsWith<InvalidValueException> { OrderQuantity.of(OrderQuantity.MAX_VALUE + 1) }
    }
}
