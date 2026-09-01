package com.example.shopapi.core.enums

/**
 * 운영이 정하는 상품 상태. 컬럼으로 저장한다.
 *
 * 손님에게 보이는 상태는 이것이 아니라 [ProductAvailability] 다. 재고에 따라 갈리는
 * 품절을 여기 두지 않는 이유는 저장된 상태와 재고 수량이 어긋날 수 있기 때문이다(ADR 0014).
 */
enum class ProductStatus {
    /** 등록만 됐다. 카탈로그에 노출되지 않는다 */
    DRAFT,

    ON_SALE,

    /** 일시 판매 중지. 목록에서는 빠지지만 상세는 그대로 열린다 */
    SUSPENDED,

    /** 종단 상태. 상태를 바꾸는 어떤 전이도 받지 않는다 */
    DISCONTINUED,
}
