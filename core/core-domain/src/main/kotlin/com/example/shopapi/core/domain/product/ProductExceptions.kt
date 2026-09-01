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
