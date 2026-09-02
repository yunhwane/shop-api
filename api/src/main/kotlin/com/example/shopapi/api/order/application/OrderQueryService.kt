package com.example.shopapi.api.order.application

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.port.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증된 회원 본인의 주문 조회.
 *
 * 소유권 검사가 이 클래스가 하는 일의 전부다. 컨트롤러에 두지 않는 이유는 그 판정이
 * HTTP 관심사가 아니기 때문이다(ADR 0016).
 */
@Service
class OrderQueryService(
    private val orders: OrderRepository,
) {
    @Transactional(readOnly = true)
    fun findMine(
        buyerId: Long,
        id: Long,
    ): Order {
        val order = orders.findById(id) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) {
            throw OrderNotFoundException()
        }
        return order
    }

    /**
     * 다음 쪽이 있는지 알기 위해 요청한 것보다 한 개를 더 읽어 돌려준다(ADR 0015 와 같은 방식).
     * 자르고 커서를 만드는 일은 [com.example.shopapi.api.order.dto.OrderListResponse] 가 한다.
     */
    @Transactional(readOnly = true)
    fun listMine(
        buyerId: Long,
        cursor: Long?,
        size: Int,
    ): List<Order> {
        if (size !in MIN_SIZE..MAX_SIZE) {
            throw InvalidValueException("size", "$MIN_SIZE~$MAX_SIZE 사이여야 합니다.")
        }
        return orders.findByBuyerId(buyerId, cursor, size + 1)
    }

    companion object {
        const val MIN_SIZE = 1
        const val MAX_SIZE = 100
        const val DEFAULT_SIZE = 20
    }
}
