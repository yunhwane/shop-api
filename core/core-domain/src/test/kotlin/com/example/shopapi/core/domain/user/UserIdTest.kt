package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserIdTest {
    @Test
    fun `영문과 숫자 조합을 받는다`() {
        assertEquals("alice01", UserId.of("alice01").value)
    }

    /** 정규화하지 않으면 Alice 와 alice 가 별개 계정이 된다(ADR 0005). */
    @Test
    fun `소문자로 정규화한다`() {
        assertEquals(UserId.of("alice01"), UserId.of("Alice01"))
    }

    @Test
    fun `앞뒤 공백은 무시한다`() {
        assertEquals("alice01", UserId.of("  alice01  ").value)
    }

    @Test
    fun `너무 짧으면 거부한다`() {
        assertFailsWith<InvalidValueException> { UserId.of("abc") }
    }

    @Test
    fun `너무 길면 거부한다`() {
        assertFailsWith<InvalidValueException> { UserId.of("a".repeat(UserId.MAX_LENGTH + 1)) }
    }

    @Test
    fun `영문과 숫자가 아닌 문자를 거부한다`() {
        listOf("alice_01", "alice-01", "앨리스01", "alice 01", "alice@01").forEach {
            assertFailsWith<InvalidValueException>("'$it' 은 거부되어야 한다") { UserId.of(it) }
        }
    }

    @Test
    fun `어느 필드가 틀렸는지 알려준다`() {
        val exception = assertFailsWith<InvalidValueException> { UserId.of("ab") }

        assertEquals("userId", exception.field)
    }
}
