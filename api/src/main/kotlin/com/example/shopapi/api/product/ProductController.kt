package com.example.shopapi.api.product

import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.product.application.ProductQueryService
import com.example.shopapi.api.product.dto.ProductDetailResponse
import com.example.shopapi.api.product.dto.ProductListResponse
import com.example.shopapi.api.product.support.ProductCursors
import com.example.shopapi.core.domain.product.ProductSearchCriteria
import com.example.shopapi.core.enums.ProductCategory
import com.example.shopapi.core.enums.ProductSort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 카탈로그. 인증이 필요 없다.
 *
 * 이번 범위에서 밖으로 나가는 상품 엔드포인트는 이 둘뿐이다. 등록과 수정은 유스케이스로만
 * 존재하고 HTTP 로 열지 않는다 - 이유는 `ProductRegistrationService` 에 있다.
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productQueryService: ProductQueryService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) category: ProductCategory?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "LATEST") sort: ProductSort,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "${ProductSearchCriteria.DEFAULT_SIZE}") size: Int,
    ): ResponseEntity<ApiResponse<ProductListResponse>> {
        val criteria =
            ProductSearchCriteria.of(
                category = category,
                keyword = keyword,
                sort = sort,
                cursor = cursor?.let { ProductCursors.decode(it) },
                size = size,
            )
        return ResponseEntity.ok(ApiResponse.of(ProductListResponse.from(productQueryService.list(criteria))))
    }

    @GetMapping("/{id}")
    fun detail(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> =
        ResponseEntity.ok(ApiResponse.of(ProductDetailResponse.from(productQueryService.findPublic(id))))
}
