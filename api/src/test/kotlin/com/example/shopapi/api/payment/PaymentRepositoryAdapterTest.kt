package com.example.shopapi.api.payment

import com.example.shopapi.api.order.application.PlaceOrderCommand
import com.example.shopapi.api.order.application.PlaceOrderItemCommand
import com.example.shopapi.api.order.application.PlaceOrderService
import com.example.shopapi.api.payment.application.ReadyPaymentService
import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.payment.Payment
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.port.PaymentRepository
import com.example.shopapi.core.enums.PaymentStatus
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `PaymentRepositoryAdapter` 가 지키기로 한 계약을 직접 확인한다.
 *
 * `ConfirmPaymentService` 를 거치지 않고 [PaymentRepository] 를 직접 호출해, 조건부 원자
 * 갱신(`markDoneIfReady`/`markFailedIfReady`)의 WHERE 절과 바인딩이 실제로 맞는지
 * `OrderRepositoryAdapterTest` 와 같은 방식으로 본다.
 */
@SpringBootTest
@TestPropertySource(properties = ["mail.provider=log", "catalog.seed=false", "payment.toss.provider=fake"])
class PaymentRepositoryAdapterTest(
    @param:Autowired private val payments: PaymentRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
    @param:Autowired private val placeOrderService: PlaceOrderService,
    @param:Autowired private val readyPaymentService: ReadyPaymentService,
) {
    @Test
    fun `READY 일 때만 DONE 으로 전이하며 결제 키와 승인 시각을 함께 저장한다`() {
        val payment = readyPayment()
        val paymentId = requireNotNull(payment.id)
        val key = PaymentKey.of("adapter-test-key")
        val approvedAt = Instant.parse("2026-09-02T00:00:00Z")
        val now = approvedAt.plusSeconds(1)

        assertEquals(true, payments.markDoneIfReady(paymentId, key, approvedAt, now))

        val reloaded = assertNotNull(payments.findById(paymentId))
        assertEquals(PaymentStatus.DONE, reloaded.status)
        assertEquals(key, reloaded.paymentKey)
        assertEquals(approvedAt, reloaded.approvedAt)
        assertEquals(now, reloaded.updatedAt)
    }

    @Test
    fun `이미 DONE 인 결제는 다시 DONE 으로 전이시키지 못한다`() {
        val payment = readyPayment()
        val paymentId = requireNotNull(payment.id)
        val now = Instant.parse("2026-09-02T00:00:00Z")
        payments.markDoneIfReady(paymentId, PaymentKey.of("first-key"), now, now)

        val transitioned = payments.markDoneIfReady(paymentId, PaymentKey.of("second-key"), now, now)

        assertEquals(false, transitioned)
        assertEquals(PaymentKey.of("first-key"), assertNotNull(payments.findById(paymentId)).paymentKey)
    }

    @Test
    fun `READY 일 때만 FAILED 로 전이한다`() {
        val payment = readyPayment()
        val paymentId = requireNotNull(payment.id)
        val now = Instant.parse("2026-09-02T00:00:00Z")

        assertEquals(true, payments.markFailedIfReady(paymentId, now))
        val reloaded = assertNotNull(payments.findById(paymentId))
        assertEquals(PaymentStatus.FAILED, reloaded.status)
        assertNull(reloaded.paymentKey)

        assertEquals(false, payments.markFailedIfReady(paymentId, now.plusSeconds(1)))
    }

    /** 이 경로를 열어 두면 상태 전이가 조건부 원자 갱신을 우회하게 된다(ADR 0017) */
    @Test
    fun `이미 저장된 결제는 save 로 다시 저장할 수 없다`() {
        val payment = readyPayment()

        assertFailsWith<InvalidDataAccessApiUsageException> { payments.save(payment) }
    }

    private fun readyPayment(): Payment {
        val productId = onSaleProduct(5)
        val order =
            placeOrderService.place(1L, PlaceOrderCommand(listOf(PlaceOrderItemCommand(productId, 1))))
        return readyPaymentService.ready(1L, requireNotNull(order.id)).payment
    }

    private fun draftProduct(stock: Int): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand("어댑터 결제 테스트 상품", "설명", 10_000, ProductCategory.ETC, stock))
                .id,
        )

    private fun onSaleProduct(stock: Int): Long = draftProduct(stock).also { managementService.startSelling(it) }
}
