package com.example.shopapi.core.enums

/**
 * 손님에게 보이는 상품 상태. [ProductStatus] 와 재고 수량에서 파생한다.
 *
 * 저장하지 않는다. 저장하면 재고와 어긋날 수 있고, 어긋났을 때 어느 쪽이 진실인지
 * 판단할 근거가 없다(ADR 0014).
 */
enum class ProductAvailability {
    ON_SALE,

    /** 판매 중이지만 재고가 없다 */
    SOLD_OUT,

    /** 판매 중이 아니다. 이유는 알려주지 않는다 */
    UNAVAILABLE,
}
