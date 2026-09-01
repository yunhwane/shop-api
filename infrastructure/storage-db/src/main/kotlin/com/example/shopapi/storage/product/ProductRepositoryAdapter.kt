package com.example.shopapi.storage.product

import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductNotFoundException
import com.example.shopapi.core.domain.product.ProductPage
import com.example.shopapi.core.domain.product.ProductSearchCriteria
import com.example.shopapi.core.enums.ProductStatus
import org.springframework.stereotype.Repository

@Repository
internal class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
    private val queryRepository: ProductQueryRepository,
) : ProductRepository {
    /**
     * 새 상품은 그대로 넣고, 이미 있는 상품은 **카탈로그 필드만** 덮어쓴다.
     *
     * 도메인 객체로 엔티티를 통째로 다시 만들어 넘기면 JPA 가 전체 컬럼을 쓰면서
     * `stock_quantity` 까지 되돌려 놓는다. 가격을 고치는 트랜잭션이 그 사이에 커밋된
     * 주문의 차감을 지워, 조건부 원자 갱신으로 막은 초과 판매가 다시 열린다(ADR 0014).
     *
     * `UserRepositoryAdapter` 와 달리 `saveAndFlush` 를 쓰지 않는다.
     * 번역해야 할 유니크 제약이 이 테이블에 없다.
     */
    override fun save(product: Product): Product {
        val id = product.id ?: return jpaRepository.save(ProductJpaEntity.from(product)).toDomain()
        val entity = jpaRepository.findById(id).orElseThrow { ProductNotFoundException() }
        entity.applyCatalog(product)
        return jpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Product? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findOnSalePage(criteria: ProductSearchCriteria): ProductPage =
        ProductPage.of(
            fetched = queryRepository.findOnSalePage(criteria).map { it.toDomain() },
            sort = criteria.sort,
            size = criteria.size,
        )

    /**
     * 수량과 판매 상태 검사를 여기서 하지 않는다. 조건이 전부 UPDATE 문 안에 있어야
     * 경합 판정과 같은 원자 단위에 들어간다(ADR 0014). 여기서 걸러 예외를 던지면
     * `@Repository` 의 예외 변환이 그것을 Spring 의 DAO 예외로 감싸, 어댑터가
     * 프레임워크 타입을 밖으로 흘리기도 한다.
     */
    override fun decreaseStockIfEnough(
        id: Long,
        quantity: Int,
    ): Boolean = jpaRepository.decreaseStockIfEnough(id, quantity, ProductStatus.ON_SALE) == 1

    override fun increaseStock(
        id: Long,
        quantity: Int,
    ): Boolean = jpaRepository.increaseStock(id, quantity) == 1

    override fun adjustStock(
        id: Long,
        quantity: Int,
    ): Boolean = jpaRepository.adjustStock(id, quantity) == 1
}
