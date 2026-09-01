package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductNameTest {
    @Test
    fun `앞뒤 공백을 지운다`() {
        assertEquals("옥스퍼드 셔츠", ProductName.of("  옥스퍼드 셔츠 ").value)
    }

    /**
     * `UserId` 와 달리 대소문자와 가운데 공백을 그대로 둔다. 상품명은 유일성 판단에
     * 쓰이지 않아 정규화할 이유가 없고, 정규화하면 의도한 표기가 바뀐다.
     */
    @Test
    fun `대소문자와 가운데 공백을 건드리지 않는다`() {
        assertEquals("iPhone  Case", ProductName.of("iPhone  Case").value)
    }

    @Test
    fun `비어 있으면 거부한다`() {
        listOf("", "   ").forEach {
            assertFailsWith<InvalidValueException>("'$it' 은 거부되어야 한다") { ProductName.of(it) }
        }
    }

    @Test
    fun `길이 상한을 넘으면 거부한다`() {
        ProductName.of("a".repeat(ProductName.MAX_LENGTH))

        assertFailsWith<InvalidValueException> { ProductName.of("a".repeat(ProductName.MAX_LENGTH + 1)) }
    }

    @Test
    fun `줄바꿈이나 탭을 거부한다`() {
        listOf("셔츠\n특가", "셔츠\t특가").forEach {
            assertFailsWith<InvalidValueException>("'$it' 은 거부되어야 한다") { ProductName.of(it) }
        }
    }
}
