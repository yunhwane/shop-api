package com.example.shopapi.core.domain.shipping

import com.example.shopapi.core.domain.common.CorruptedDataException
import com.example.shopapi.core.domain.common.InvalidValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 배송지 각 조각의 형식 규칙을 고정한다.
 *
 * 값 자체보다 "어느 필드가 왜 거절됐는가"가 응답에 실려 나가므로([InvalidValueException.field]),
 * 필드 이름까지 함께 본다.
 */
class ShippingAddressTest {
    @Test
    fun `수령인은 앞뒤 공백을 떼고 담는다`() {
        assertEquals("전윤환", RecipientName.of("  전윤환  ").value)
    }

    @Test
    fun `빈 수령인은 거부한다`() {
        val e = assertFailsWith<InvalidValueException> { RecipientName.of("   ") }

        assertEquals("recipientName", e.field)
    }

    @Test
    fun `연락처는 하이픈이 있어도 없어도 받는다`() {
        assertEquals("010-1234-5678", PhoneNumber.of("010-1234-5678").value)
        assertEquals("01012345678", PhoneNumber.of("01012345678").value)
    }

    /** 표기를 서버가 바꾸면 사용자가 입력한 그대로가 아니게 된다 - `ProductName` 과 같은 이유다 */
    @Test
    fun `연락처의 하이픈을 서버가 떼지 않는다`() {
        assertEquals("010-1234-5678", PhoneNumber.of("010-1234-5678").value)
    }

    @Test
    fun `숫자가 아닌 연락처는 거부한다`() {
        val e = assertFailsWith<InvalidValueException> { PhoneNumber.of("010-일이삼사-5678") }

        assertEquals("phone", e.field)
    }

    @Test
    fun `우편번호는 5자리 숫자만 받는다`() {
        assertEquals("04524", PostalCode.of("04524").value)
        assertFailsWith<InvalidValueException> { PostalCode.of("135-090") }
        assertFailsWith<InvalidValueException> { PostalCode.of("123456") }
    }

    @Test
    fun `기본주소가 상한을 넘으면 거부한다`() {
        val e = assertFailsWith<InvalidValueException> { AddressLine1.of("가".repeat(AddressLine1.MAX_LENGTH + 1)) }

        assertEquals("addressLine1", e.field)
    }

    @Test
    fun `줄바꿈이 든 주소는 거부한다`() {
        assertFailsWith<InvalidValueException> { AddressLine1.of("서울 중구\n세종대로 110") }
    }

    /** 서버 데이터 문제를 클라이언트 입력 탓으로 돌리지 않는다(ADR 0007) */
    @Test
    fun `복원한 값이 규칙을 어기면 저장된 값 문제로 답한다`() {
        assertFailsWith<CorruptedDataException> { PostalCode.reconstitute("045240") }
    }
}
