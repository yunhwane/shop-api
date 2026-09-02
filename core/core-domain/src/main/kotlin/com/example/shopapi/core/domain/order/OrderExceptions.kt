package com.example.shopapi.core.domain.order

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

/**
 * 주문이 없거나, 있어도 이 요청의 주체 소유가 아니다.
 *
 * 두 경우를 같은 응답으로 묶는다. 구분해서 알려주면 순차 발급되는 주문 ID 로 타인의
 * 주문 존재 여부를 추측할 수 있게 된다(ADR 0016).
 */
class OrderNotFoundException : DomainException(ErrorCode.ORDER_NOT_FOUND)

class OrderNotCancellableException : DomainException(ErrorCode.ORDER_NOT_CANCELLABLE)
