package com.example.shopapi.api.order

import com.example.shopapi.api.order.application.CancelOrderService
import com.example.shopapi.api.order.application.PlaceOrderCommand
import com.example.shopapi.api.order.application.PlaceOrderItemCommand
import com.example.shopapi.api.order.application.PlaceOrderService
import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.order.OrderNotCancellableException
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 동시 취소 요청이 재고를 두 번 복원하지 않는지 본다(ADR 0016).
 *
 * `ProductStockConcurrencyTest` 와 같은 이유로 단일 스레드로 두 번 부르는 테스트는
 * 의미가 없다 - 조회 후 저장 방식으로 구현해도 그런 테스트는 통과한다.
 */
@SpringBootTest
@TestPropertySource(properties = ["mail.provider=log", "catalog.seed=false"])
class OrderCancelConcurrencyTest(
    @param:Autowired private val products: ProductRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
    @param:Autowired private val placeOrderService: PlaceOrderService,
    @param:Autowired private val cancelOrderService: CancelOrderService,
) {
    private val buyerId = 42L

    @Test
    fun `같은 주문에 취소가 몰려도 재고는 한 번만 복원된다`() {
        val stock = 5
        val quantity = 2
        val productId = onSaleProduct(stock)
        val orderId =
            requireNotNull(
                placeOrderService
                    .place(
                        buyerId,
                        PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, quantity)), shippingAddressCommand()),
                    ).id,
            )
        val afterOrder = stockOf(productId)
        val attempts = 20

        val succeeded = raceToCancel(orderId, attempts)

        assertEquals(1, succeeded, "이미 취소된 주문에 대한 취소 요청은 실패해야 한다")
        assertEquals(afterOrder + quantity, stockOf(productId), "재고 복원은 정확히 한 번만 일어나야 한다")
    }

    private fun raceToCancel(
        orderId: Long,
        attempts: Int,
    ): Int {
        val succeeded = AtomicInteger()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(attempts)
        try {
            val futures =
                (1..attempts).map {
                    pool.submit {
                        start.await()
                        try {
                            cancelOrderService.cancel(buyerId, orderId)
                            succeeded.incrementAndGet()
                        } catch (e: OrderNotCancellableException) {
                            // 경합에서 진 요청. 기대한 결과다
                        }
                    }
                }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        return succeeded.get()
    }

    private fun stockOf(id: Long): Int = assertNotNull(products.findById(id)).stockQuantity.value

    private fun draftProduct(stock: Int): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand("동시 취소 상품", "설명", 10_000, ProductCategory.ETC, stock))
                .id,
        )

    private fun onSaleProduct(stock: Int): Long = draftProduct(stock).also { managementService.startSelling(it) }
}
