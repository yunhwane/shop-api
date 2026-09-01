package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StockQuantityTest {
    @Test
    fun `음수를 거부한다`() {
        assertFailsWith<InvalidValueException> { StockQuantity.of(-1) }
    }

    @Test
    fun `상한을 넘으면 거부한다`() {
        StockQuantity.of(StockQuantity.MAX_VALUE)

        assertFailsWith<InvalidValueException> { StockQuantity.of(StockQuantity.MAX_VALUE + 1) }
    }

    @Test
    fun `0 을 품절로 판단한다`() {
        assertEquals(true, StockQuantity.of(0).isZero)
        assertEquals(false, StockQuantity.of(1).isZero)
    }

    @Test
    fun `요청 수량을 채울 수 있는지 본다`() {
        val stock = StockQuantity.of(3)

        assertEquals(true, stock.isAtLeast(3))
        assertEquals(false, stock.isAtLeast(4))
    }
}
