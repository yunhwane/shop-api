package com.example.shopapi.storage.shipping

import com.example.shopapi.core.enums.ShipmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

internal interface ShipmentJpaRepository : JpaRepository<ShipmentJpaEntity, Long> {
    fun findByOrderId(orderId: Long): ShipmentJpaEntity?

    /**
     * `PREPARING` 일 때만 `SHIPPING` 으로 전이하며 발송 시각을 함께 쓴다.
     * `OrderJpaRepository.cancelIfPlaced` 와 같은 조건부 원자 갱신이다(ADR 0016, ADR 0020).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update ShipmentJpaEntity s set s.status = :next, s.shippedAt = :now, s.updatedAt = :now " +
            "where s.id = :id and s.status = :current",
    )
    fun startShippingIfPreparing(
        @Param("id") id: Long,
        @Param("current") current: ShipmentStatus,
        @Param("next") next: ShipmentStatus,
        @Param("now") now: Instant,
    ): Int

    /** `SHIPPING` 일 때만 `DELIVERED` 로 전이하며 완료 시각을 함께 쓴다 */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update ShipmentJpaEntity s set s.status = :next, s.deliveredAt = :now, s.updatedAt = :now " +
            "where s.id = :id and s.status = :current",
    )
    fun markDeliveredIfShipping(
        @Param("id") id: Long,
        @Param("current") current: ShipmentStatus,
        @Param("next") next: ShipmentStatus,
        @Param("now") now: Instant,
    ): Int
}
