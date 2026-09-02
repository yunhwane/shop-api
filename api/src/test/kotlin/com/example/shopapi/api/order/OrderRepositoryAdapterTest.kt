package com.example.shopapi.api.order

import com.example.shopapi.api.order.application.PlaceOrderCommand
import com.example.shopapi.api.order.application.PlaceOrderItemCommand
import com.example.shopapi.api.order.application.PlaceOrderService
import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.enums.OrderStatus
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.test.context.TestPropertySource
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * `OrderRepositoryAdapter` 가 지키기로 한 계약을 직접 확인한다.
 *
 * `CancelOrderService` 를 거치지 않고 [OrderRepository] 를 직접 호출해, 어댑터 자체의
 * 계약(취소 시각을 함께 쓴다, 이미 저장된 주문은 save 를 거부한다)을 서비스 계층의
 * 정상 흐름과 분리해서 본다.
 */
@SpringBootTest
@TestPropertySource(properties = ["mail.provider=log", "catalog.seed=false"])
class OrderRepositoryAdapterTest(
    @param:Autowired private val orders: OrderRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
    @param:Autowired private val placeOrderService: PlaceOrderService,
) {
    /**
     * 초 단위로 잘라 비교한다. DB 컬럼의 소수점 이하 정밀도는 방언·모드마다 달라
     * (H2 를 MySQL 호환 모드로 쓴다) 왕복하면서 그 아래 자리가 잘릴 수 있다 - 이 테스트가
     * 확인하려는 것은 그 정밀도가 아니라 취소 시각이 실제로 쓰였는가다.
     */
    @Test
    fun `취소 시각을 함께 저장한다`() {
        val productId = onSaleProduct(5)
        val order = placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))
        val orderId = requireNotNull(order.id)
        val cancelledAt = order.createdAt.plusSeconds(60)

        assertEquals(true, orders.cancelIfPlaced(orderId, cancelledAt))

        val reloaded = assertNotNull(orders.findById(orderId))
        assertEquals(cancelledAt.truncatedTo(ChronoUnit.SECONDS), reloaded.updatedAt.truncatedTo(ChronoUnit.SECONDS))
        assertNotEquals(
            reloaded.createdAt.truncatedTo(ChronoUnit.SECONDS),
            reloaded.updatedAt.truncatedTo(ChronoUnit.SECONDS),
        )
    }

    /** [OrderRepository.cancelIfPaid] 는 [OrderRepository.cancelIfPlaced] 와 같은 모양의 조건부 원자 갱신이다(ADR 0018) */
    @Test
    fun `PAID 일 때만 CANCELLED 로 전이한다`() {
        val productId = onSaleProduct(5)
        val order = placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))
        val orderId = requireNotNull(order.id)
        val now = order.createdAt
        orders.markPaidIfPlaced(orderId, now)

        assertEquals(true, orders.cancelIfPaid(orderId, now.plusSeconds(60)))
        assertEquals(OrderStatus.CANCELLED, assertNotNull(orders.findById(orderId)).status)

        assertEquals(false, orders.cancelIfPaid(orderId, now.plusSeconds(120)))
    }

    /** 결제 시도 하나가 이미 이 주문을 선점하고 있으면 다른 시도는 선점할 수 없다(ADR 0019) */
    @Test
    fun `아직 아무도 선점하지 않은 PLACED 주문만 선점된다`() {
        val productId = onSaleProduct(5)
        val order = placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))
        val orderId = requireNotNull(order.id)
        val now = order.createdAt

        assertEquals(true, orders.claimPaymentIfPlaced(orderId, paymentId = 1L, now))

        assertEquals(false, orders.claimPaymentIfPlaced(orderId, paymentId = 2L, now.plusSeconds(1)))
    }

    /** 선점을 놓아주면 다른 결제 시도가 같은 주문을 다시 선점할 수 있다(ADR 0019) */
    @Test
    fun `놓아준 선점은 다른 결제 시도가 다시 잡을 수 있다`() {
        val productId = onSaleProduct(5)
        val order = placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))
        val orderId = requireNotNull(order.id)
        val now = order.createdAt
        orders.claimPaymentIfPlaced(orderId, paymentId = 1L, now)

        assertEquals(true, orders.releaseClaimedPayment(orderId, paymentId = 1L, now.plusSeconds(1)))
        assertEquals(true, orders.claimPaymentIfPlaced(orderId, paymentId = 2L, now.plusSeconds(2)))
    }

    @Test
    fun `자신이 선점하지 않은 결제 시도는 놓아줄 수 없다`() {
        val productId = onSaleProduct(5)
        val order = placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))
        val orderId = requireNotNull(order.id)
        val now = order.createdAt
        orders.claimPaymentIfPlaced(orderId, paymentId = 1L, now)

        assertEquals(false, orders.releaseClaimedPayment(orderId, paymentId = 2L, now.plusSeconds(1)))
        assertEquals(false, orders.claimPaymentIfPlaced(orderId, paymentId = 3L, now.plusSeconds(2)))
    }

    /**
     * 이 경로를 열어 두면 언젠가 취소에도 쓰여, cancelIfPlaced 가 막은 동시 취소 경합이 되살아난다(ADR 0016).
     *
     * 어댑터는 `IllegalStateException` 을 던지지만, `@Repository` 의 예외 변환이
     * `IllegalStateException`/`IllegalArgumentException` 을 무조건
     * `InvalidDataAccessApiUsageException` 으로 감싼다 - 빈을 프록시로 통해 호출하는
     * 이 테스트는 그 감싸진 타입으로 받는다.
     */
    @Test
    fun `이미 저장된 주문은 save 로 다시 저장할 수 없다`() {
        val productId = onSaleProduct(5)
        val order = placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))

        assertFailsWith<InvalidDataAccessApiUsageException> { orders.save(order) }
    }

    private fun draftProduct(stock: Int): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand("어댑터 테스트 상품", "설명", 10_000, ProductCategory.ETC, stock))
                .id,
        )

    private fun onSaleProduct(stock: Int): Long = draftProduct(stock).also { managementService.startSelling(it) }
}
