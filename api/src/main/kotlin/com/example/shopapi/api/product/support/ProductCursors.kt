package com.example.shopapi.api.product.support

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.product.ProductCursor
import com.example.shopapi.core.enums.ProductSort
import java.util.Base64

/**
 * 커서를 HTTP 로 실어 나르는 형식.
 *
 * 인코딩이 api 에 있는 이유는 [ProductCursor] 가 도메인 타입이고, 그것을 문자열로 만드는
 * 일이 표현 계층의 관심사이기 때문이다.
 *
 * **불투명하게 만드는 것이 목적이다.** 구조가 눈에 보이면 클라이언트가 커서를 직접
 * 조립하기 시작하고, 그 순간 내부 정렬 키가 공개 계약이 된다(ADR 0015).
 *
 * 서명하지 않는다. 담기는 값이 공개 카탈로그의 가격과 id 뿐이라 감출 것이 없다.
 * 다만 **위조한 커서로 임의 위치를 열 수 있다**는 뜻이므로, 여기에 권한이나 필터 신뢰에
 * 관한 정보를 담아서는 안 된다. 커서는 "어디부터"만 말한다.
 */
object ProductCursors {
    private const val DELIMITER = '|'
    private const val NO_PRICE = "-"
    private const val FIELD_COUNT = 3

    fun encode(cursor: ProductCursor): String {
        val plain = "${cursor.sort.name}$DELIMITER${cursor.price?.amount ?: NO_PRICE}$DELIMITER${cursor.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.toByteArray())
    }

    fun decode(raw: String): ProductCursor {
        val fields = decode64(raw).split(DELIMITER)
        if (fields.size != FIELD_COUNT) {
            reject()
        }
        return ProductCursor.of(
            sort = ProductSort.entries.find { it.name == fields[0] } ?: reject(),
            price = if (fields[1] == NO_PRICE) null else money(fields[1]),
            id = fields[2].toLongOrNull() ?: reject(),
        )
    }

    private fun decode64(raw: String): String =
        try {
            String(Base64.getUrlDecoder().decode(raw))
        } catch (e: IllegalArgumentException) {
            throw InvalidValueException("cursor", "커서 형식이 올바르지 않습니다. ${e.message}")
        }

    /**
     * 범위를 벗어난 금액은 **커서가 깨진 것**이지 요청한 가격이 잘못된 것이 아니다.
     * [Money] 가 던지는 `price` 필드 오류를 그대로 흘리면 클라이언트가 보내지도 않은
     * 필드를 고치라는 말을 듣는다.
     */
    private fun money(raw: String): Money =
        try {
            Money.of(raw.toLongOrNull() ?: reject())
        } catch (e: InvalidValueException) {
            throw InvalidValueException("cursor", "커서 형식이 올바르지 않습니다. ${e.reason}")
        }

    private fun reject(): Nothing = throw InvalidValueException("cursor", "커서 형식이 올바르지 않습니다.")
}
