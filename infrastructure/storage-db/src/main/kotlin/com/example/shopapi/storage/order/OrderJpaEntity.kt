package com.example.shopapi.storage.order

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderLine
import com.example.shopapi.core.domain.order.OrderQuantity
import com.example.shopapi.core.domain.product.ProductName
import com.example.shopapi.core.enums.OrderStatus
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.Instant

/**
 * 주문의 영속성 모델.
 *
 * 라인은 독자적으로 조회·식별될 필요가 없는 값이라 별도 `@Entity` 를 두지 않고
 * `@ElementCollection` 으로 담는다 — `Product` 가 단일 엔티티로 끝난 것과 같은 이유다(ADR 0011).
 * `@OrderColumn` 으로 담긴 순서를 그대로 보존해, 주문할 때 나열한 순서가 조회에서도 유지되게 한다.
 */
@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_orders_buyer_id", columnList = "buyer_id, id DESC")],
)
class OrderJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "buyer_id", nullable = false)
    var buyerId: Long,
    /**
     * `FetchType.EAGER` 로 둔다. 기본값인 지연 로딩으로는 트랜잭션·영속성 컨텍스트
     * 바깥에서 [lines] 를 읽으면 `LazyInitializationException` 이 난다. 주문은 항상
     * 라인을 포함한 하나의 애그리게이트로 다뤄지고, 한 주문의 라인 수도 작아 즉시
     * 로딩의 비용이 문제가 되지 않는다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = [JoinColumn(name = "order_id")])
    @OrderColumn(name = "line_no")
    var lines: MutableList<OrderLineEmbeddable>,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    fun toDomain(): Order =
        Order.reconstitute(
            id = requireNotNull(id) { "저장된 주문이어야 한다" },
            buyerId = buyerId,
            lines = lines.map { it.toDomain() },
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun from(order: Order): OrderJpaEntity =
            OrderJpaEntity(
                id = order.id,
                buyerId = order.buyerId,
                lines = order.lines.map { OrderLineEmbeddable.from(it) }.toMutableList(),
                status = order.status,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt,
            )
    }
}

@Embeddable
class OrderLineEmbeddable(
    @Column(name = "product_id", nullable = false)
    var productId: Long,
    @Column(name = "product_name", nullable = false, length = 100)
    var productName: String,
    @Column(name = "unit_price", nullable = false)
    var unitPrice: Long,
    @Column(name = "quantity", nullable = false)
    var quantity: Int,
) {
    fun toDomain(): OrderLine =
        OrderLine(
            productId = productId,
            productName = ProductName.reconstitute(productName),
            unitPrice = Money.reconstitute(unitPrice),
            quantity = OrderQuantity.reconstitute(quantity),
        )

    companion object {
        fun from(line: OrderLine): OrderLineEmbeddable =
            OrderLineEmbeddable(
                productId = line.productId,
                productName = line.productName.value,
                unitPrice = line.unitPrice.amount,
                quantity = line.quantity.value,
            )
    }
}
