package com.example.shopapi.core.domain.shipping

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

/**
 * 배송 조회가 빈손으로 끝났다.
 *
 * 본인 주문이 아닌 경우는 이 예외가 아니라 `OrderNotFoundException` 이다 - 남의 주문은
 * 존재 자체를 숨긴다(ADR 0016). 이 예외는 "내 주문은 맞는데 아직 결제 전이라 배송이
 * 없다" 를 뜻하므로 숨길 것이 없다(ADR 0020).
 */
class ShipmentNotFoundException : DomainException(ErrorCode.SHIPMENT_NOT_FOUND)
