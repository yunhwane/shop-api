package com.example.shopapi.storage.payment

import com.example.shopapi.core.enums.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

internal interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderIdAndTossOrderId(
        orderId: Long,
        tossOrderId: String,
    ): PaymentJpaEntity?

    /**
     * 정상 경로에서는 최대 한 건이다 -
     * [com.example.shopapi.core.domain.port.PaymentRepository.findDoneByOrderId] 의 문서를
     * 참고한다(ADR 0018). 목록으로 받는 이유는 그 전제가 깨진 경우를 어댑터가 조용히
     * 삼키지 않고 호출자가 판단하게 하기 위해서다.
     */
    fun findByOrderIdAndStatus(
        orderId: Long,
        status: PaymentStatus,
    ): List<PaymentJpaEntity>

    /**
     * `READY` 일 때만 `DONE` 으로 전이한다. `OrderJpaRepository.cancelIfPlaced` 와 같은
     * 조건부 원자 갱신이다(ADR 0016, ADR 0017).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update PaymentJpaEntity p set p.status = :next, p.paymentKey = :paymentKey, " +
            "p.approvedAt = :approvedAt, p.updatedAt = :now " +
            "where p.id = :id and p.status = :current",
    )
    fun markDoneIfReady(
        @Param("id") id: Long,
        @Param("current") current: PaymentStatus,
        @Param("next") next: PaymentStatus,
        @Param("paymentKey") paymentKey: String,
        @Param("approvedAt") approvedAt: Instant,
        @Param("now") now: Instant,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update PaymentJpaEntity p set p.status = :next, p.updatedAt = :now " +
            "where p.id = :id and p.status = :current",
    )
    fun markFailedIfReady(
        @Param("id") id: Long,
        @Param("current") current: PaymentStatus,
        @Param("next") next: PaymentStatus,
        @Param("now") now: Instant,
    ): Int

    /** `DONE` 일 때만 `CANCELLED` 로 전이한다. 위 둘과 같은 조건부 원자 갱신이다(ADR 0018) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update PaymentJpaEntity p set p.status = :next, p.updatedAt = :now " +
            "where p.id = :id and p.status = :current",
    )
    fun markCancelledIfDone(
        @Param("id") id: Long,
        @Param("current") current: PaymentStatus,
        @Param("next") next: PaymentStatus,
        @Param("now") now: Instant,
    ): Int
}
