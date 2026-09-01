package com.example.shopapi.core.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmailTest {
    @Test
    fun `일반적인 주소를 받는다`() {
        assertEquals("user@example.com", Email.of("user@example.com").value)
    }

    @Test
    fun `소문자로 정규화한다`() {
        assertEquals(Email.of("user@example.com"), Email.of("User@Example.COM"))
    }

    @Test
    fun `서브도메인과 플러스 주소를 허용한다`() {
        listOf("user+tag@example.com", "user@mail.example.co.kr", "user.name@example.com").forEach {
            Email.of(it)
        }
    }

    @Test
    fun `형식이 아니면 거부한다`() {
        listOf("user", "user@", "@example.com", "user@example", "us er@example.com", "").forEach {
            assertFailsWith<InvalidValueException>("'$it' 은 거부되어야 한다") { Email.of(it) }
        }
    }

    @Test
    fun `길이 상한을 넘으면 거부한다`() {
        val tooLong = "a".repeat(Email.MAX_LENGTH) + "@example.com"

        assertFailsWith<InvalidValueException> { Email.of(tooLong) }
    }
}
