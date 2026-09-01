package com.example.shopapi.api.product.application

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.product.Product
import com.example.shopapi.core.domain.product.ProductDescription
import com.example.shopapi.core.domain.product.ProductName
import com.example.shopapi.core.domain.product.StockQuantity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 등록.
 *
 * **컨트롤러가 없다.** 권한 모델이 없는 상태에서 쓰기 엔드포인트를 열면, 인증만으로는
 * 인가가 되지 않아 로그인한 아무 회원이나 카탈로그를 고칠 수 있다. 도메인 규칙은 지금
 * 확정하고, "누가 할 수 있는가" 가 정해질 때 컨트롤러만 얹는다. 이 시그니처는 그때
 * 바뀌지 않는다.
 *
 * 지금 이 유스케이스를 부르는 것은 카탈로그 시드와 테스트다.
 */
@Service
class ProductRegistrationService(
    private val products: ProductRepository,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun register(command: RegisterProductCommand): Product {
        val product =
            Product.register(
                name = ProductName.of(command.name),
                description = ProductDescription.of(command.description),
                price = Money.of(command.price),
                category = command.category,
                stockQuantity = StockQuantity.of(command.stockQuantity),
                now = timeProvider.now(),
            )
        return products.save(product)
    }
}
