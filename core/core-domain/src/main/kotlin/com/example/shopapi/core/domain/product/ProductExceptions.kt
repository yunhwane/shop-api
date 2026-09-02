package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

/**
 * 상품이 없거나, 있어도 공개되지 않는 상태다.
 *
 * `DRAFT` 와 `DISCONTINUED` 를 "없다"와 같은 응답으로 묶는다. 구분해서 알려주면
 * 아직 공개하지 않은 상품의 존재가 드러난다.
 */
class ProductNotFoundException : DomainException(ErrorCode.PRODUCT_NOT_FOUND)

class ProductNotOnSaleException : DomainException(ErrorCode.PRODUCT_NOT_ON_SALE)

class ProductDiscontinuedException : DomainException(ErrorCode.PRODUCT_DISCONTINUED)

/**
 * 주문 시점에 재고가 모자란다.
 *
 * [Product.ensureOrderable] 의 사전 검사 실패로 던져진다. 실제 방어선은
 * `ProductRepository.decreaseStockIfEnough` 의 원자 갱신이고(ADR 0014), 이 예외는
 * 그 문장이 실패했을 때도 같은 사유로 재사용된다 — 사전 검사를 통과한 뒤 경합에서
 * 졌다는 뜻이기 때문이다.
 */
class InsufficientStockException(
    val productId: Long,
) : DomainException(ErrorCode.INSUFFICIENT_STOCK)
