package com.example.shopapi.api.product.application

import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductNotFoundException
import com.example.shopapi.core.domain.product.ProductPage
import com.example.shopapi.core.domain.product.ProductSearchCriteria
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 공개 카탈로그 조회.
 *
 * 이 클래스가 하는 일은 **공개 노출 규칙을 적용하는 것 하나**다. 컨트롤러에 두지 않는
 * 이유는 그 판정이 HTTP 관심사가 아니기 때문이다.
 */
@Service
class ProductQueryService(
    private val products: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun list(criteria: ProductSearchCriteria): ProductPage = products.findOnSalePage(criteria)

    /**
     * 상세로 열어 줄 수 있는 상품만 돌려준다.
     *
     * `DRAFT` 와 `DISCONTINUED` 는 존재하지 않는 것과 같은 응답을 준다. 구분해서
     * 알려주면 아직 공개하지 않은 상품의 존재가 드러난다.
     */
    @Transactional(readOnly = true)
    fun findPublic(id: Long): Product =
        products.findById(id)?.takeIf { it.isPubliclyVisible } ?: throw ProductNotFoundException()
}
