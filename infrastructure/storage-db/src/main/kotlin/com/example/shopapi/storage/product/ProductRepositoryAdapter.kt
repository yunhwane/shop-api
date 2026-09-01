package com.example.shopapi.storage.product

import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductPage
import com.example.shopapi.core.domain.product.ProductSearchCriteria
import org.springframework.stereotype.Repository

@Repository
internal class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
    private val queryRepository: ProductQueryRepository,
) : ProductRepository {
    /**
     * `UserRepositoryAdapter` 와 달리 `saveAndFlush` 를 쓰지 않는다.
     * 번역해야 할 유니크 제약이 이 테이블에 없다.
     */
    override fun save(product: Product): Product = jpaRepository.save(ProductJpaEntity.from(product)).toDomain()

    override fun findById(id: Long): Product? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findOnSalePage(criteria: ProductSearchCriteria): ProductPage =
        ProductPage.of(
            fetched = queryRepository.findOnSalePage(criteria).map { it.toDomain() },
            sort = criteria.sort,
            size = criteria.size,
        )

    /**
     * 수량 검사를 여기서 하지 않는다. 조건이 전부 UPDATE 문 안에 있어야 경합 판정과
     * 같은 원자 단위에 들어간다(ADR 0014). 여기서 걸러 예외를 던지면 `@Repository` 의
     * 예외 변환이 그것을 Spring 의 DAO 예외로 감싸, 어댑터가 프레임워크 타입을 밖으로 흘린다.
     */
    override fun decreaseStockIfEnough(
        id: Long,
        quantity: Int,
    ): Boolean = jpaRepository.decreaseStockIfEnough(id, quantity) == 1

    override fun increaseStock(
        id: Long,
        quantity: Int,
    ) {
        jpaRepository.increaseStock(id, quantity)
    }
}
