package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductPage
import com.example.shopapi.core.domain.product.ProductSearchCriteria

/**
 * 상품 저장소.
 *
 * 시그니처에 Spring 의 `Pageable` / `Page` 가 없다. core 는 프레임워크에 의존하지 않고,
 * `Pageable` 은 `pageNumber` 라는 offset 개념을 들고 있어 커서 방식과 맞지도 않는다(ADR 0015).
 */
interface ProductRepository {
    fun save(product: Product): Product

    fun findById(id: Long): Product?

    /**
     * 판매 중인 상품만 한 쪽 돌려준다.
     *
     * 목록에서 `SUSPENDED` 를 빼는 것은 공개 노출 규칙이다. 살 수 없는 물건이 진열되면 안 된다.
     */
    fun findOnSalePage(criteria: ProductSearchCriteria): ProductPage

    /**
     * 재고가 충분할 때만 줄이고, 줄였는지 알려준다.
     *
     * 조회하고 빼서 저장하는 방식으로는 같은 상품에 들어온 동시 주문이 둘 다 통과한다.
     * 둘 다 같은 재고를 읽기 때문이다. 유니크 제약이 중복 가입의 진짜 방어선인 것과
     * 같은 이유로(ADR 0005), 경합은 DB 가 판정한다(ADR 0014).
     *
     * 구현은 벌크 UPDATE 라 영속성 컨텍스트를 우회한다. 호출 전에 읽어 둔 엔티티는
     * 낡은 재고를 들고 있다.
     *
     * [quantity] 가 0 이하면 아무것도 바꾸지 않고 `false` 를 돌려준다.
     */
    fun decreaseStockIfEnough(
        id: Long,
        quantity: Int,
    ): Boolean

    /**
     * 주문 취소·환불에 따른 복원.
     *
     * `StockQuantity` 의 상한을 지나지 않는다. 복원되는 수량은 앞서 차감된 수량이라
     * 실무상 상한을 넘지 않지만, 그 불변식이 값 객체가 아니라 호출자에게 걸려 있다(ADR 0014).
     */
    fun increaseStock(
        id: Long,
        quantity: Int,
    )
}
