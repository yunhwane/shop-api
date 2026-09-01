package com.example.shopapi.core.domain.common

import com.example.shopapi.core.domain.user.EncodedPassword
import com.example.shopapi.core.domain.user.UserId
import com.example.shopapi.core.enums.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 같은 값이라도 어디서 왔는지에 따라 실패의 의미가 다르다는 것을 고정한다.
 */
class CorruptedDataTest {
    @Test
    fun `저장된 값이 규칙을 어기면 서버 오류로 다룬다`() {
        val exception = assertFailsWith<CorruptedDataException> { Email.reconstitute("not-an-email") }

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.errorCode)
        assertEquals("email", exception.field)
    }

    @Test
    fun `같은 값이라도 입력으로 들어오면 클라이언트 오류다`() {
        val exception = assertFailsWith<InvalidValueException> { Email.of("not-an-email") }

        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /** 값에는 해시나 토큰이 섞일 수 있다. 어긴 필드 이름만으로 충분하다. */
    @Test
    fun `메시지에 저장된 값을 담지 않는다`() {
        val tooLong = "x".repeat(EncodedPassword.MAX_LENGTH + 1)

        val exception = assertFailsWith<CorruptedDataException> { EncodedPassword.reconstitute(tooLong) }

        assertEquals(false, exception.message.contains(tooLong))
    }

    @Test
    fun `원인이 된 검증 실패를 잃지 않는다`() {
        val exception = assertFailsWith<CorruptedDataException> { UserId.reconstitute("ab") }

        assertEquals("userId", exception.cause.field)
    }

    @Test
    fun `규칙을 지키는 값은 그대로 복원된다`() {
        assertEquals(UserId.of("alice01"), UserId.reconstitute("alice01"))
    }
}
