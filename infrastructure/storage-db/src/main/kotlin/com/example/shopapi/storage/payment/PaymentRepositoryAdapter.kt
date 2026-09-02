package com.example.shopapi.storage.payment

import com.example.shopapi.core.domain.payment.Payment
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import com.example.shopapi.core.domain.port.PaymentRepository
import com.example.shopapi.core.enums.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
internal class PaymentRepositoryAdapter(
    private val jpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    /** [Payment.id] 가 이미 있는 값을 받으면 실패한다 - 상태 전이는 [markDoneIfReady] 를 쓴다 */
    override fun save(payment: Payment): Payment {
        check(payment.id == null) { "이미 저장된 결제는 save 로 갱신할 수 없다. 상태 전이는 markDoneIfReady/markFailedIfReady 를 쓴다" }
        return jpaRepository.save(PaymentJpaEntity.from(payment)).toDomain()
    }

    override fun findById(id: Long): Payment? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByOrderIdAndTossOrderId(
        orderId: Long,
        tossOrderId: TossOrderId,
    ): Payment? = jpaRepository.findByOrderIdAndTossOrderId(orderId, tossOrderId.value)?.toDomain()

    override fun findDoneByOrderId(orderId: Long): Payment? =
        jpaRepository.findByOrderIdAndStatus(orderId, PaymentStatus.DONE).firstOrNull()?.toDomain()

    override fun markDoneIfReady(
        id: Long,
        paymentKey: PaymentKey,
        approvedAt: Instant,
        now: Instant,
    ): Boolean =
        jpaRepository.markDoneIfReady(
            id,
            PaymentStatus.READY,
            PaymentStatus.DONE,
            paymentKey.value,
            approvedAt,
            now,
        ) == 1

    override fun markFailedIfReady(
        id: Long,
        now: Instant,
    ): Boolean = jpaRepository.markFailedIfReady(id, PaymentStatus.READY, PaymentStatus.FAILED, now) == 1

    override fun markCancelledIfDone(
        id: Long,
        now: Instant,
    ): Boolean = jpaRepository.markCancelledIfDone(id, PaymentStatus.DONE, PaymentStatus.CANCELLED, now) == 1
}
