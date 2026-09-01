package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.ProductAvailability
import com.example.shopapi.core.enums.ProductCategory
import com.example.shopapi.core.enums.ProductStatus
import java.time.Instant

/**
 * 판매 단위 하나.
 *
 * 색상·사이즈 같은 변형을 두지 않는다. 가격과 재고를 상품이 직접 든다(ADR 0011).
 *
 * 공개 식별자를 따로 두지 않고 [id] 를 그대로 노출한다. `EmailVerification` 이 식별자를
 * 둘로 나눈 것은 두 주체가 각각 다른 열쇠를 쥐어야 했기 때문인데(ADR 0002), 카탈로그는
 * 공개라 감출 것이 없다.
 */
class Product(
    val id: Long?,
    val name: ProductName,
    val description: ProductDescription,
    val price: Money,
    val category: ProductCategory,
    val stockQuantity: StockQuantity,
    val status: ProductStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 손님에게 보이는 상태. 저장하지 않고 상태와 재고에서 파생한다(ADR 0014) */
    val availability: ProductAvailability
        get() =
            when {
                status != ProductStatus.ON_SALE -> ProductAvailability.UNAVAILABLE
                stockQuantity.isZero -> ProductAvailability.SOLD_OUT
                else -> ProductAvailability.ON_SALE
            }

    /**
     * 상세 조회로 열어 줄 상태인가.
     *
     * `SUSPENDED` 를 포함한다. 목록에서는 빠지지만 상세는 열린다 — 이미 링크를 가진
     * 사람에게 404 를 주면 "일시적으로 판매하지 않는다"가 "없어졌다"로 읽힌다.
     */
    val isPubliclyVisible: Boolean
        get() = status == ProductStatus.ON_SALE || status == ProductStatus.SUSPENDED

    /**
     * 이 수량을 지금 재고로 채울 수 있는가.
     *
     * **표시와 사전 안내용이다.** 이 판정을 믿고 차감하면 안 된다 — 동시 요청이 같은
     * 재고를 읽고 모두 통과한다. 실제 차감은
     * `ProductRepository.decreaseStockIfEnough` 하나뿐이다(ADR 0014).
     */
    fun canFulfill(quantity: Int): Boolean =
        status == ProductStatus.ON_SALE && quantity > 0 && stockQuantity.isAtLeast(quantity)

    fun changeDetails(
        name: ProductName,
        description: ProductDescription,
        category: ProductCategory,
        now: Instant,
    ): Product {
        ensureNotDiscontinued()
        return copyWith(name = name, description = description, category = category, updatedAt = now)
    }

    fun changePrice(
        price: Money,
        now: Instant,
    ): Product {
        ensureNotDiscontinued()
        return copyWith(price = price, updatedAt = now)
    }

    /**
     * 재고를 다시 센 결과로 덮어쓴다. 입고와 실사 정정용이다.
     *
     * 주문에 따른 차감·복원은 이 경로를 쓰지 않는다. 읽은 값에 더하고 빼면 동시 요청이
     * 서로의 결과를 덮어쓴다(ADR 0014).
     */
    fun adjustStock(
        stockQuantity: StockQuantity,
        now: Instant,
    ): Product {
        ensureNotDiscontinued()
        return copyWith(stockQuantity = stockQuantity, updatedAt = now)
    }

    /** 이미 판매 중이면 멱등하게 자신을 돌려준다 */
    fun startSelling(now: Instant): Product {
        ensureNotDiscontinued()
        if (status == ProductStatus.ON_SALE) {
            return this
        }
        return copyWith(status = ProductStatus.ON_SALE, updatedAt = now)
    }

    fun suspendSelling(now: Instant): Product {
        ensureNotDiscontinued()
        if (status == ProductStatus.SUSPENDED) {
            return this
        }
        // DRAFT 는 아직 팔린 적이 없어 중지할 것이 없다. 조용히 넘기면 호출자는
        // 중지됐다고 믿는데 상태는 그대로다.
        if (status != ProductStatus.ON_SALE) {
            throw ProductNotOnSaleException()
        }
        return copyWith(status = ProductStatus.SUSPENDED, updatedAt = now)
    }

    /**
     * 단종. 종단 상태이며 되돌릴 수 없다.
     *
     * 되돌릴 수 있게 하면 주문 이력이 참조하는 상품이 다시 팔리는데, 그때 가격과 사양이
     * 예전 주문과 같다는 보장이 없다. 다시 팔려면 새로 등록한다.
     *
     * 행을 지우지 않는 이유도 같다. 주문 이력이 상품을 참조한다.
     */
    fun discontinue(now: Instant): Product {
        if (status == ProductStatus.DISCONTINUED) {
            return this
        }
        return copyWith(status = ProductStatus.DISCONTINUED, updatedAt = now)
    }

    private fun ensureNotDiscontinued() {
        if (status == ProductStatus.DISCONTINUED) {
            throw ProductDiscontinuedException()
        }
    }

    private fun copyWith(
        name: ProductName = this.name,
        description: ProductDescription = this.description,
        price: Money = this.price,
        category: ProductCategory = this.category,
        stockQuantity: StockQuantity = this.stockQuantity,
        status: ProductStatus = this.status,
        updatedAt: Instant,
    ): Product =
        Product(
            id = id,
            name = name,
            description = description,
            price = price,
            category = category,
            stockQuantity = stockQuantity,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    override fun toString(): String = "Product(id=$id, name=$name, status=$status)"

    companion object {
        /**
         * 아직 저장되지 않은 신규 상품. [ProductStatus.DRAFT] 로 시작한다.
         *
         * 등록과 판매 개시를 분리해서, 가격이나 재고가 정해지지 않은 상품이 카탈로그에
         * 노출되는 경로를 없앤다.
         */
        fun register(
            name: ProductName,
            description: ProductDescription,
            price: Money,
            category: ProductCategory,
            stockQuantity: StockQuantity,
            now: Instant,
        ): Product =
            Product(
                id = null,
                name = name,
                description = description,
                price = price,
                category = category,
                stockQuantity = stockQuantity,
                status = ProductStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            )
    }
}
