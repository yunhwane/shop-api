package com.example.shopapi.storage.order

import com.example.shopapi.core.enums.OrderStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

internal interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    /** [pageable] 은 크기만 쓴다. 페이지 번호는 언제나 0이다 - 다음 쪽은 커서로 이어 받는다 */
    fun findByBuyerIdOrderByIdDesc(
        buyerId: Long,
        pageable: Pageable,
    ): List<OrderJpaEntity>

    fun findByBuyerIdAndIdLessThanOrderByIdDesc(
        buyerId: Long,
        id: Long,
        pageable: Pageable,
    ): List<OrderJpaEntity>

    /**
     * [current] 상태일 때만 [next] 로 전이하며 [now] 를 `updated_at` 에 함께 쓰고,
     * 전이했는지 알려준다.
     *
     * `ProductJpaRepository.decreaseStockIfEnough` 와 같은 조건부 원자 갱신이다
     * (ADR 0014, ADR 0016). 영향 행 수가 0 이면 이미 다른 요청이 먼저 전이시킨 것이다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update OrderJpaEntity o set o.status = :next, o.updatedAt = :now " +
            "where o.id = :id and o.status = :current",
    )
    fun cancelIfPlaced(
        @Param("id") id: Long,
        @Param("current") current: OrderStatus,
        @Param("next") next: OrderStatus,
        @Param("now") now: Instant,
    ): Int

    /** [cancelIfPlaced] 와 같은 모양의 조건부 원자 갱신이다(ADR 0017) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update OrderJpaEntity o set o.status = :next, o.updatedAt = :now " +
            "where o.id = :id and o.status = :current",
    )
    fun markPaidIfPlaced(
        @Param("id") id: Long,
        @Param("current") current: OrderStatus,
        @Param("next") next: OrderStatus,
        @Param("now") now: Instant,
    ): Int

    /** 위 둘과 같은 모양의 조건부 원자 갱신이다(ADR 0018) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update OrderJpaEntity o set o.status = :next, o.updatedAt = :now " +
            "where o.id = :id and o.status = :current",
    )
    fun cancelIfPaid(
        @Param("id") id: Long,
        @Param("current") current: OrderStatus,
        @Param("next") next: OrderStatus,
        @Param("now") now: Instant,
    ): Int

    /**
     * `status = :placed` 이고 `activePaymentId` 가 비어 있을 때만 [paymentId] 에게
     * 선점시킨다. 진짜 단일 행 CAS 다(ADR 0019) - 결제 시도 쪽 행을 서로 다른 두 개로
     * 나눠 걸었던 첫 시도(서브쿼리로 형제 행을 보는 방식)는 READ_COMMITTED 에서 두
     * 트랜잭션이 서로의 미커밋 상태를 보지 못해 둘 다 통과해 버렸다 - 이 메서드는 항상
     * 같은 주문 행 하나를 두고 겨루므로 표준 행 잠금만으로 정확히 하나만 통과한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update OrderJpaEntity o set o.activePaymentId = :paymentId, o.updatedAt = :now " +
            "where o.id = :id and o.status = :placed and o.activePaymentId is null",
    )
    fun claimPaymentIfPlaced(
        @Param("id") id: Long,
        @Param("paymentId") paymentId: Long,
        @Param("placed") placed: OrderStatus,
        @Param("now") now: Instant,
    ): Int

    /** 이 주문을 선점 중인 결제 시도가 [paymentId] 일 때만 선점을 풀어준다(ADR 0019) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update OrderJpaEntity o set o.activePaymentId = null, o.updatedAt = :now " +
            "where o.id = :id and o.activePaymentId = :paymentId",
    )
    fun releaseClaimedPayment(
        @Param("id") id: Long,
        @Param("paymentId") paymentId: Long,
        @Param("now") now: Instant,
    ): Int
}
