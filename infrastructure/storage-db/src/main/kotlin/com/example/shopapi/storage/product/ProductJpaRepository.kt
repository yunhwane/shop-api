package com.example.shopapi.storage.product

import com.example.shopapi.core.enums.ProductStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

internal interface ProductJpaRepository : JpaRepository<ProductJpaEntity, Long> {
    /**
     * 재고 조건을 UPDATE 문 안에 넣어 DB 가 경합을 판정하게 한다(ADR 0014).
     * 영향 행 수가 0 이면 다른 요청이 먼저 가져간 것이다.
     *
     * `:quantity > 0` 도, 판매 상태도 같은 자리에 둔다. 수량 조건을 빠뜨리면 0 이나 음수를
     * 받았을 때 조건이 항상 참이 되어 **차감이 재고를 늘리고**, 상태 조건을 빠뜨리면 주문이
     * 날아오는 동안 단종된 상품이 그대로 팔린다. 이 문 하나가 재고 불변식의 전부이므로
     * 조건도 여기 모아 둔다.
     *
     * `updated_at` 을 건드리지 않는다. 그 값은 카탈로그를 고친 시각이고, 재고 차감은
     * 카탈로그 수정이 아니다. 시각을 받으면 포트 시그니처에 시계가 끌려 들어온다.
     *
     * 여기 `@Transactional` 이 붙는 이유는 유스케이스의 트랜잭션 경계를 옮기려는 것이
     * 아니다(ADR 0003). 갱신 쿼리는 트랜잭션 없이는 실행되지 않는데, 이 문장 하나가
     * 곧 원자 단위라 호출자가 트랜잭션을 열지 않아도 성립해야 한다. REQUIRED 라
     * 호출자가 이미 열었으면 그 트랜잭션에 합류한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update ProductJpaEntity p set p.stockQuantity = p.stockQuantity - :quantity " +
            "where p.id = :id and p.status = :status " +
            "and :quantity > 0 and p.stockQuantity >= :quantity",
    )
    fun decreaseStockIfEnough(
        @Param("id") id: Long,
        @Param("quantity") quantity: Int,
        @Param("status") status: ProductStatus,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update ProductJpaEntity p set p.stockQuantity = p.stockQuantity + :quantity " +
            "where p.id = :id and :quantity > 0",
    )
    fun increaseStock(
        @Param("id") id: Long,
        @Param("quantity") quantity: Int,
    ): Int

    /**
     * 센 값으로 덮어쓴다. 규칙 판정은 도메인이 이미 했고 여기서는 쓰기만 한다.
     *
     * 카탈로그 저장 경로를 쓰지 않는 이유는 [decreaseStockIfEnough] 와 같다.
     * 재고는 언제나 한 문장으로 쓴다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductJpaEntity p set p.stockQuantity = :quantity where p.id = :id")
    fun adjustStock(
        @Param("id") id: Long,
        @Param("quantity") quantity: Int,
    ): Int
}
