package com.example.shopapi.api.product.application

import com.example.shopapi.core.enums.ProductCategory

/**
 * 상품 등록 입력.
 *
 * 값 객체가 아니라 원시 타입을 받는다. 형식 검증과 정규화는 유스케이스가 값 객체를
 * 만들 때 일어난다 - `SignUpCommand` 와 같은 방식이다.
 */
data class RegisterProductCommand(
    val name: String,
    val description: String,
    val price: Long,
    val category: ProductCategory,
    val stockQuantity: Int,
)

data class ChangeProductDetailsCommand(
    val name: String,
    val description: String,
    val category: ProductCategory,
)
