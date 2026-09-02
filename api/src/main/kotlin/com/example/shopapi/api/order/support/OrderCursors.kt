package com.example.shopapi.api.order.support

import com.example.shopapi.core.domain.common.InvalidValueException
import java.util.Base64

/**
 * 커서를 HTTP 로 실어 나르는 형식. [com.example.shopapi.api.product.support.ProductCursors]
 * 와 같은 이유로 불투명하게 만든다 - 다만 여기 담기는 값은 마지막으로 읽은 주문 [id] 하나뿐이다.
 * 필터가 없어 정렬 기준을 함께 담을 이유가 없다.
 */
object OrderCursors {
    fun encode(id: Long): String = Base64.getUrlEncoder().withoutPadding().encodeToString(id.toString().toByteArray())

    fun decode(raw: String): Long =
        try {
            String(Base64.getUrlDecoder().decode(raw)).toLong()
        } catch (e: IllegalArgumentException) {
            throw InvalidValueException("cursor", "커서 형식이 올바르지 않습니다.")
        }
}
