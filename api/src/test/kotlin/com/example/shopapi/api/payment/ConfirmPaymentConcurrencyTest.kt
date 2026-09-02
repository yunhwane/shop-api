package com.example.shopapi.api.payment

import com.example.shopapi.api.order.application.PlaceOrderCommand
import com.example.shopapi.api.order.application.PlaceOrderItemCommand
import com.example.shopapi.api.order.application.PlaceOrderService
import com.example.shopapi.api.order.shippingAddressCommand
import com.example.shopapi.api.payment.application.ConfirmPaymentCommand
import com.example.shopapi.api.payment.application.ConfirmPaymentService
import com.example.shopapi.api.payment.application.ReadyPaymentService
import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.order.OrderNotPayableException
import com.example.shopapi.core.domain.payment.PaymentNotReadyException
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.PaymentRepository
import com.example.shopapi.core.enums.OrderStatus
import com.example.shopapi.core.enums.PaymentStatus
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
 * 같은 주문에 걸린 서로 다른 결제 시도가 동시에 confirm 되어도 하나만 `DONE` 이 되는지
 * 본다(ADR 0019). 클레임 없이는 각 시도가 독립적으로 Toss 승인을 받아 실제 이중 결제로
 * 이어진다 - `payment.toss.provider=fake` 는 매 호출을 항상 승인하므로, 클레임이 없다면
 * 여럿이 동시에 `DONE` 이 되는 것을 이 테스트가 그대로 드러낸다.
 *
 * `OrderCancelConcurrencyTest` 와 같은 이유로 단일 스레드로 순차 호출하는 테스트는 의미가
 * 없다 - 클레임 없이 구현해도 순차 호출은 항상 통과한다.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "catalog.seed=false",
        "payment.toss.provider=fake",
    ],
)
class ConfirmPaymentConcurrencyTest(
    @param:Autowired private val orders: OrderRepository,
    @param:Autowired private val payments: PaymentRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
    @param:Autowired private val placeOrderService: PlaceOrderService,
    @param:Autowired private val readyPaymentService: ReadyPaymentService,
    @param:Autowired private val confirmPaymentService: ConfirmPaymentService,
) {
    private val buyerId = 77L

    @Test
    fun `같은 주문의 결제 시도 여럿을 동시에 확정해도 하나만 DONE 이 된다`() {
        val productId = onSaleProduct(5)
        val orderId =
            requireNotNull(
                placeOrderService
                    .place(
                        buyerId,
                        PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1)), shippingAddressCommand()),
                    ).id,
            )
        val attempts = 10
        val paymentIds = (1..attempts).map { requireNotNull(readyPaymentService.ready(buyerId, orderId).payment.id) }

        val succeeded = raceToConfirm(orderId, paymentIds)

        assertEquals(1, succeeded, "이중 결제로 이어지는 동시 확정은 하나만 성공해야 한다")
        assertEquals(OrderStatus.PAID, assertNotNull(orders.findById(orderId)).status)
        val doneCount = paymentIds.count { assertNotNull(payments.findById(it)).status == PaymentStatus.DONE }
        assertEquals(1, doneCount, "DONE 인 결제 시도는 정확히 하나여야 한다")
    }

    private fun raceToConfirm(
        orderId: Long,
        paymentIds: List<Long>,
    ): Int {
        val succeeded = AtomicInteger()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(paymentIds.size)
        try {
            val futures =
                paymentIds.map { paymentId ->
                    pool.submit {
                        start.await()
                        val payment = assertNotNull(payments.findById(paymentId))
                        val command =
                            ConfirmPaymentCommand(
                                tossOrderId = payment.tossOrderId.value,
                                paymentKey = "fake-payment-key-$paymentId",
                                amount = payment.amount.amount,
                            )
                        try {
                            confirmPaymentService.confirm(buyerId, orderId, command)
                            succeeded.incrementAndGet()
                        } catch (e: PaymentNotReadyException) {
                            // 클레임에서 진 요청. 기대한 결과다
                        } catch (e: OrderNotPayableException) {
                            // 다른 시도가 먼저 주문을 PAID 로 옮긴 뒤에 읽은 요청. 기대한 결과다
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

    private fun draftProduct(stock: Int): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand("동시 확정 상품", "설명", 10_000, ProductCategory.ETC, stock))
                .id,
        )

    private fun onSaleProduct(stock: Int): Long = draftProduct(stock).also { managementService.startSelling(it) }
}
