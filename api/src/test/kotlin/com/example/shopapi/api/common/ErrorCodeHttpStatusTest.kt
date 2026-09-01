package com.example.shopapi.api.common

import com.example.shopapi.core.enums.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorCodeHttpStatusTest {
    @Test
    fun `모든 에러 코드에 상태 코드가 매핑돼 있다`() {
        val unmapped = ErrorCode.entries - ErrorCodeHttpStatus.mappedCodes()

        assertTrue(unmapped.isEmpty(), "상태 코드 매핑이 없는 ErrorCode: $unmapped")
    }

    @Test
    fun `타입 URI 를 코드에서 유도한다`() {
        assertEquals(
            "/problems/duplicate-user-id",
            ProblemDetails.typeUri(ErrorCode.DUPLICATE_USER_ID).toString(),
        )
    }
}
