package com.example.shopapi.storage.product

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductDescription
import com.example.shopapi.core.domain.product.ProductName
import com.example.shopapi.core.domain.product.StockQuantity
import com.example.shopapi.core.enums.ProductCategory
import com.example.shopapi.core.enums.ProductStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant

/**
 * 상품의 영속성 모델. 도메인 모델([Product])과 별개 클래스다.
 *
 * **유니크 제약이 없다.** 상품명은 식별자가 아니라 표시용 이름이고, 같은 이름의 다른
 * 상품이 실재한다. `users` 가 제약을 가진 것은 그쪽 값이 식별자이기 때문이다(ADR 0005).
 *
 * 인덱스가 넷인 것은 **필터 × 정렬 조합만큼 필요하다**는 뜻이다. 정렬 옵션을 하나 더
 * 넣으면 둘이 는다(ADR 0015).
 *
 * `@DynamicUpdate` 는 성능을 위한 것이 아니라 **정확성을 위한 것이다.** 기본값으로
 * Hibernate 는 바뀐 컬럼만 고르는 것이 아니라 매핑된 컬럼을 전부 UPDATE 문에 싣는다.
 * 그러면 [applyCatalog] 가 재고를 건드리지 않아도 메모리에 남아 있던 옛 재고가 그대로
 * 쓰여, 그 사이에 커밋된 차감이 지워진다. 둘은 함께여야 뜻을 이룬다(ADR 0014).
 */
@Entity
@DynamicUpdate
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_status_id", columnList = "status, id DESC"),
        Index(name = "idx_products_status_cat_id", columnList = "status, category, id DESC"),
        Index(name = "idx_products_status_price_id", columnList = "status, price, id"),
        Index(name = "idx_products_status_cat_price_id", columnList = "status, category, price, id"),
    ],
)
class ProductJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "description", nullable = false, length = 2000)
    var description: String,
    @Column(name = "price", nullable = false)
    var price: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    var category: ProductCategory,
    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ProductStatus,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    /**
     * 카탈로그 정보만 덮어쓴다. **재고는 건드리지 않는다.**
     *
     * 도메인 객체가 들고 있는 재고까지 쓰면, 읽은 뒤 저장하기까지의 사이에 커밋된 차감이
     * 지워진다. 조건부 원자 갱신으로 막은 초과 판매가 이 경로로 되돌아온다(ADR 0014).
     *
     * 클래스에 붙은 `@DynamicUpdate` 가 없으면 이 노력이 헛돈다. 그 설명은 클래스 KDoc 에 있다.
     */
    fun applyCatalog(product: Product) {
        name = product.name.value
        description = product.description.value
        price = product.price.amount
        category = product.category
        status = product.status
        updatedAt = product.updatedAt
    }

    fun toDomain(): Product =
        Product(
            id = id,
            name = ProductName.reconstitute(name),
            description = ProductDescription.reconstitute(description),
            price = Money.reconstitute(price),
            category = category,
            stockQuantity = StockQuantity.reconstitute(stockQuantity),
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun from(product: Product): ProductJpaEntity =
            ProductJpaEntity(
                id = product.id,
                name = product.name.value,
                description = product.description.value,
                price = product.price.amount,
                category = product.category,
                stockQuantity = product.stockQuantity.value,
                status = product.status,
                createdAt = product.createdAt,
                updatedAt = product.updatedAt,
            )
    }
}
