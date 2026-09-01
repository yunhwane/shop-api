package com.example.shopapi.api.product.dto

import com.example.shopapi.api.product.support.ProductCursors
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductPage
import com.example.shopapi.core.enums.ProductAvailability
import com.example.shopapi.core.enums.ProductCategory

/**
 * 목록 항목.
 *
 * `description` 을 담지 않는다. 2000자가 목록의 모든 행에 실릴 이유가 없다.
 * 저장된 [com.example.shopapi.core.enums.ProductStatus] 도 담지 않는다 —
 * 손님에게 필요한 것은 살 수 있는가([availability])뿐이다.
 */
data class ProductSummaryResponse(
    val id: Long,
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val availability: ProductAvailability,
) {
    companion object {
        fun from(product: Product): ProductSummaryResponse =
            ProductSummaryResponse(
                id = requireNotNull(product.id) { "저장된 상품이어야 한다" },
                name = product.name.value,
                price = product.price.amount,
                category = product.category,
                availability = product.availability,
            )
    }
}

data class ProductListResponse(
    val items: List<ProductSummaryResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: ProductPage): ProductListResponse =
            ProductListResponse(
                items = page.items.map { ProductSummaryResponse.from(it) },
                nextCursor = page.nextCursor?.let { ProductCursors.encode(it) },
                hasNext = page.hasNext,
            )
    }
}

/**
 * 상세.
 *
 * 재고 수량을 그대로 노출한다. "품절 임박" 표시에 쓰이는 값이고 감춰서 얻는 것이 없다.
 * 정확한 수량을 가리고 싶어지면 구간으로 바꾼다 — 값 하나를 좁히는 일이라 계약 변경이 작다.
 */
data class ProductDetailResponse(
    val id: Long,
    val name: String,
    val description: String,
    val price: Long,
    val category: ProductCategory,
    val availability: ProductAvailability,
    val stockQuantity: Int,
) {
    companion object {
        fun from(product: Product): ProductDetailResponse =
            ProductDetailResponse(
                id = requireNotNull(product.id) { "저장된 상품이어야 한다" },
                name = product.name.value,
                description = product.description.value,
                price = product.price.amount,
                category = product.category,
                availability = product.availability,
                stockQuantity = product.stockQuantity.value,
            )
    }
}
