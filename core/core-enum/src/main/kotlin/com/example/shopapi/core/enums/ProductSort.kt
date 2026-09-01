package com.example.shopapi.core.enums

/**
 * 상품 목록의 정렬 기준.
 *
 * 항목을 늘리면 복합 인덱스가 필터 조합만큼 함께 늘어난다(ADR 0015). 값싼 확장이 아니다.
 */
enum class ProductSort {
    /** 최근 등록순 */
    LATEST,

    PRICE_ASC,

    PRICE_DESC,

    ;

    /** 커서에 가격을 실어야 하는가 */
    val ordersByPrice: Boolean
        get() = this == PRICE_ASC || this == PRICE_DESC
}
