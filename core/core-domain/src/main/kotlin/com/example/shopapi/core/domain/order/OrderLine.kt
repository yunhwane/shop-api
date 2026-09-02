package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.product.ProductName

/**
 * 주문 한 줄. 상품 자체가 아니라 **주문 시점의 스냅샷**을 담는다.
 *
 * [productName] 과 [unitPrice] 를 상품에서 다시 읽지 않고 여기 그대로 든다. 상품 이름이나
 * 가격이 바뀌어도, 심지어 단종되어도 지난 주문의 내역은 그때 그대로여야 한다.
 *
 * 필드가 각각 자기 값 객체([ProductName], [Money], [OrderQuantity])의 검증을 이미
 * 통과했으므로, 여기서는 조합에 대한 추가 검증이 없어 별도의 `of`/`reconstitute` 를
 * 두지 않는다(ADR 0007).
 */
data class OrderLine(
    val productId: Long,
    val productName: ProductName,
    val unitPrice: Money,
    val quantity: OrderQuantity,
) {
    val lineTotal: Money get() = unitPrice * quantity.value
}
