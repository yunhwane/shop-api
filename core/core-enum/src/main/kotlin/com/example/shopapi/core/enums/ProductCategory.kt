package com.example.shopapi.core.enums

/**
 * 상품 분류.
 *
 * 테이블이 아니라 enum 인 이유는 운영 중에 이것을 고칠 주체가 없기 때문이다(ADR 0013).
 * 항목을 추가하려면 배포해야 한다.
 *
 * **삭제하지 않는다.** 그 값으로 저장된 행이 남아 있으면 복원이 실패한다.
 * 쓰지 않게 된 분류는 노출에서만 뺀다.
 */
enum class ProductCategory {
    FASHION,
    BEAUTY,
    FOOD,
    LIVING,
    DIGITAL,
    SPORTS,
    BOOKS,
    ETC,
}
