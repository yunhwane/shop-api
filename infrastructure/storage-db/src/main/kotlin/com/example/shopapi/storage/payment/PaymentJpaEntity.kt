package com.example.shopapi.storage.payment

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.payment.Payment
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import com.example.shopapi.core.enums.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 결제 시도의 영속성 모델.
 *
 * `toss_order_id` 는 유니크다 - 같은 값을 두 번 발급하면 Toss 쪽 혼선으로 이어진다(ADR 0017).
 * `payment_key` 는 `DONE` 이 되기 전까지 없으므로 유니크는 `null` 을 여러 행이 가질 수 있는
 * 방식으로만 걸린다(대부분의 RDB 가 `null` 은 유니크 제약에서 서로 다른 값으로 본다).
 */
@Entity
@Table(
    name = "payments",
    indexes = [Index(name = "idx_payments_order_id", columnList = "order_id")],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payments_toss_order_id", columnNames = ["toss_order_id"]),
        UniqueConstraint(name = "uk_payments_payment_key", columnNames = ["payment_key"]),
    ],
)
class PaymentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "order_id", nullable = false)
    var orderId: Long,
    @Column(name = "toss_order_id", nullable = false, length = 64)
    var tossOrderId: String,
    @Column(name = "amount", nullable = false)
    var amount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus,
    @Column(name = "payment_key", length = 200)
    var paymentKey: String?,
    @Column(name = "approved_at")
    var approvedAt: Instant?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    fun toDomain(): Payment =
        Payment.reconstitute(
            id = requireNotNull(id) { "저장된 결제여야 한다" },
            orderId = orderId,
            tossOrderId = TossOrderId.reconstitute(tossOrderId),
            amount = Money.reconstitute(amount),
            status = status,
            paymentKey = paymentKey?.let { PaymentKey.reconstitute(it) },
            approvedAt = approvedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun from(payment: Payment): PaymentJpaEntity =
            PaymentJpaEntity(
                id = payment.id,
                orderId = payment.orderId,
                tossOrderId = payment.tossOrderId.value,
                amount = payment.amount.amount,
                status = payment.status,
                paymentKey = payment.paymentKey?.value,
                approvedAt = payment.approvedAt,
                createdAt = payment.createdAt,
                updatedAt = payment.updatedAt,
            )
    }
}
