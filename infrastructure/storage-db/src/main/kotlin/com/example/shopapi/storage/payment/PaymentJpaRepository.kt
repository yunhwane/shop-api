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
}
