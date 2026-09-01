package com.example.shopapi.api.product.application

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductDescription
import com.example.shopapi.core.domain.product.ProductName
import com.example.shopapi.core.domain.product.ProductNotFoundException
import com.example.shopapi.core.domain.product.StockQuantity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 등록된 상품을 고친다. 상태 전이 규칙은 전부 [Product] 가 판정한다.
 *
 * [ProductRegistrationService] 와 같은 이유로 컨트롤러가 없다.
 *
 * 재고 조정은 여기 있지만 **주문에 따른 차감은 여기 없다.** 그쪽은 읽은 값에 더하고 빼면
 * 동시 요청이 서로의 결과를 덮어쓰므로 `ProductRepository.decreaseStockIfEnough` 가
 * 맡는다(ADR 0014).
 */
@Service
class ProductManagementService(
    private val products: ProductRepository,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun changeDetails(
        id: Long,
        command: ChangeProductDetailsCommand,
    ): Product =
        update(id) {
            it.changeDetails(
                name = ProductName.of(command.name),
                description = ProductDescription.of(command.description),
                category = command.category,
                now = timeProvider.now(),
            )
        }

    @Transactional
    fun changePrice(
        id: Long,
        price: Long,
    ): Product = update(id) { it.changePrice(Money.of(price), timeProvider.now()) }

    /**
     * 재고 정정. 규칙은 도메인이 판정하고 쓰기는 원자 갱신으로 간다.
     *
     * [update] 를 쓰지 않는 이유는 그 경로가 카탈로그 저장이고 재고를 쓰지 않기 때문이다.
     * 재고는 언제나 한 문장으로 쓴다(ADR 0014).
     */
    @Transactional
    fun adjustStock(
        id: Long,
        stockQuantity: Int,
    ): Product {
        val product = products.findById(id) ?: throw ProductNotFoundException()
        val adjusted = product.adjustStock(StockQuantity.of(stockQuantity))
        if (!products.adjustStock(id, adjusted.stockQuantity.value)) {
            throw ProductNotFoundException()
        }
        return adjusted
    }

    @Transactional
    fun startSelling(id: Long): Product = update(id) { it.startSelling(timeProvider.now()) }

    @Transactional
    fun suspendSelling(id: Long): Product = update(id) { it.suspendSelling(timeProvider.now()) }

    @Transactional
    fun discontinue(id: Long): Product = update(id) { it.discontinue(timeProvider.now()) }

    /**
     * 여기서 찾지 못한 것은 공개 여부와 무관하게 정말로 없는 것이다.
     * 조회 경로의 숨김 규칙([ProductQueryService])과 섞지 않는다.
     */
    private fun update(
        id: Long,
        change: (Product) -> Product,
    ): Product {
        val product = products.findById(id) ?: throw ProductNotFoundException()
        return products.save(change(product))
    }
}
