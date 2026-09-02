package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductPage
import com.example.shopapi.core.domain.product.ProductSearchCriteria

/**
 * 상품 저장소.
 *
 * 시그니처에 Spring 의 `Pageable` / `Page` 가 없다. core 는 프레임워크에 의존하지 않고,
 * `Pageable` 은 `pageNumber` 라는 offset 개념을 들고 있어 커서 방식과 맞지도 않는다(ADR 0015).
 *
 * **재고를 바꾸는 길은 아래 세 메서드뿐이다.** [save] 는 재고를 쓰지 않는다(ADR 0014).
 */
interface ProductRepository {
    /**
     * 카탈로그 정보를 저장한다. **재고 수량은 쓰지 않는다.**
     *
     * 도메인 객체가 들고 있는 재고를 함께 쓰면, 읽은 뒤 저장하기까지의 사이에 커밋된
     * 차감이 지워진다 - 가격을 고치는 트랜잭션이 주문이 줄여 놓은 재고를 옛 값으로
     * 되돌려 놓는다. 조건부 원자 갱신으로 막은 초과 판매가 이 경로로 다시 들어온다.
     */
    fun save(product: Product): Product

    fun findById(id: Long): Product?

    /** 여러 상품을 한 번에 읽는다. 없는 [id] 는 결과에서 그냥 빠진다 - 호출자가 개수로 판단한다 */
    fun findAllById(ids: Collection<Long>): List<Product>

    /**
     * 판매 중인 상품만 한 쪽 돌려준다.
     *
     * 목록에서 `SUSPENDED` 를 빼는 것은 공개 노출 규칙이다. 살 수 없는 물건이 진열되면 안 된다.
     */
    fun findOnSalePage(criteria: ProductSearchCriteria): ProductPage

    /**
     * **판매 중이고** 재고가 충분할 때만 줄이고, 줄였는지 알려준다.
     *
     * 조회하고 빼서 저장하는 방식으로는 같은 상품에 들어온 동시 주문이 둘 다 통과한다.
     * 둘 다 같은 재고를 읽기 때문이다. 유니크 제약이 중복 가입의 진짜 방어선인 것과
     * 같은 이유로(ADR 0005), 경합은 DB 가 판정한다(ADR 0014).
     *
     * 판매 상태도 같은 조건절에서 본다. 호출자가 미리 읽어 확인하는 것은 **정확한 에러
     * 메시지를 위해서지 방어를 위해서가 아니다** - 읽은 뒤 차감하기까지의 사이에 단종될 수 있다.
     * 그래서 `false` 는 "재고 부족"이 아니라 "지금은 못 판다"로 읽어야 한다.
     *
     * [quantity] 가 0 이하면 아무것도 바꾸지 않고 `false` 를 돌려준다.
     *
     * 구현은 벌크 UPDATE 라 영속성 컨텍스트를 우회한다. 호출 전에 읽어 둔 엔티티는
     * 낡은 재고를 들고 있다.
     */
    fun decreaseStockIfEnough(
        id: Long,
        quantity: Int,
    ): Boolean

    /**
     * 주문 취소·환불에 따른 복원. 되돌린 행이 있었는지 알려준다.
     *
     * `StockQuantity` 의 상한을 지나지 않는다. 복원되는 수량은 앞서 차감된 수량이라
     * 실무상 상한을 넘지 않지만, 그 불변식이 값 객체가 아니라 호출자에게 걸려 있다(ADR 0014).
     */
    fun increaseStock(
        id: Long,
        quantity: Int,
    ): Boolean

    /**
     * 입고와 실사에 따른 재고 정정. 센 값으로 덮어쓴다.
     *
     * 규칙 판정(단종된 상품인가, 수량이 범위 안인가)은 도메인이 먼저 하고, 여기서는
     * 쓰기만 한다. [save] 를 거치지 않는 이유는 위와 같다 - 재고는 언제나 한 문장으로 쓴다.
     */
    fun adjustStock(
        id: Long,
        quantity: Int,
    ): Boolean
}
