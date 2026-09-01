package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawPasswordTest {
    @Test
    fun `영문과 숫자를 모두 포함하면 통과한다`() {
        assertEquals("password1", RawPassword.of("password1").value)
    }

    @Test
    fun `특수문자를 포함해도 된다`() {
        assertEquals("pass!word1", RawPassword.of("pass!word1").value)
    }

    @Test
    fun `숫자가 없으면 거부한다`() {
        assertFailsWith<InvalidValueException> { RawPassword.of("passwordonly") }
    }

    @Test
    fun `영문이 없으면 거부한다`() {
        assertFailsWith<InvalidValueException> { RawPassword.of("12345678") }
    }

    @Test
    fun `너무 짧으면 거부한다`() {
        assertFailsWith<InvalidValueException> { RawPassword.of("pass1") }
    }

    /**
     * bcrypt 는 입력을 72바이트에서 자른다. 상한이 없으면 넘치는 부분이 조용히 버려져
     * "길수록 안전하다"는 직관이 깨진다.
     */
    @Test
    fun `bcrypt 절단 지점을 넘는 길이를 거부한다`() {
        assertFailsWith<InvalidValueException> {
            RawPassword.of("a1" + "b".repeat(RawPassword.MAX_LENGTH))
        }
    }

    /** 같은 이유로 멀티바이트 문자를 막는다. 한글 64자는 UTF-8 로 192바이트다. */
    @Test
    fun `공백과 멀티바이트 문자를 거부한다`() {
        listOf("pass word1", "비밀번호1234", "password1\t").forEach {
            assertFailsWith<InvalidValueException>("'$it' 은 거부되어야 한다") { RawPassword.of(it) }
        }
    }

    @Test
    fun `문자열 표현에 평문이 담기지 않는다`() {
        assertEquals(false, RawPassword.of("password1").toString().contains("password1"))
    }
}
