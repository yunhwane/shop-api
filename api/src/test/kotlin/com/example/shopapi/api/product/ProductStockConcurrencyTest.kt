package com.example.shopapi.api.product

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.enums.ProductAvailability
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
 * 초과 판매가 실제로 막히는지 본다(ADR 0014).
 *
 * **단일 스레드로 두 번 부르는 테스트는 의미가 없다.** 조회 후 차감 방식으로 구현해도
 * 그런 테스트는 통과한다. 여기서 재는 것은 같은 순간에 들어온 요청들이 재고를 나눠
 * 갖는가이므로, 실제로 스레드를 겹쳐서 호출한다.
 */
@SpringBootTest
@TestPropertySource(properties = ["mail.provider=log", "catalog.seed=false"])
class ProductStockConcurrencyTest(
    @param:Autowired private val products: ProductRepository,
    @param:Autowired private val registrationService: ProductRegistrationService,
    @param:Autowired private val managementService: ProductManagementService,
) {
    @Test
    fun `재고가 하나면 동시에 몰려도 한 번만 성공한다`() {
        val id = onSaleProduct(stock = 1)
        val attempts = 20

        val succeeded = raceToDecrease(id, attempts, quantity = 1)

        assertEquals(1, succeeded, "재고 1 에 $attempts 개의 요청이 몰렸다")
        assertEquals(0, stockOf(id))
    }

    @Test
    fun `재고만큼만 나눠 갖는다`() {
        val stock = 7
        val id = onSaleProduct(stock = stock)

        val succeeded = raceToDecrease(id, attempts = 30, quantity = 1)

        assertEquals(stock, succeeded)
        assertEquals(0, stockOf(id))
    }

    @Test
    fun `재고보다 많은 수량은 차감하지 않는다`() {
        val id = onSaleProduct(stock = 2)

        assertEquals(false, products.decreaseStockIfEnough(id, 3))
        assertEquals(2, stockOf(id), "실패한 차감이 재고를 건드리면 안 된다")
    }

    @Test
    fun `취소로 돌아온 수량을 복원한다`() {
        val id = onSaleProduct(stock = 2)
        products.decreaseStockIfEnough(id, 2)

        products.increaseStock(id, 2)

        assertEquals(2, stockOf(id))
    }

    /** 수량 조건을 빠뜨리면 조건이 항상 참이 되어 차감이 재고를 늘린다. */
    @Test
    fun `0 이하의 수량은 아무것도 바꾸지 않는다`() {
        val id = onSaleProduct(stock = 1)

        assertEquals(false, products.decreaseStockIfEnough(id, 0))
        assertEquals(false, products.decreaseStockIfEnough(id, -1))
        products.increaseStock(id, -5)

        assertEquals(1, stockOf(id))
    }

    /** 품절은 컬럼이 아니라 재고에서 파생한다. 차감만으로 노출 상태가 따라 바뀌어야 한다. */
    @Test
    fun `재고가 바닥나면 품절로 보인다`() {
        val id = onSaleProduct(stock = 1)

        products.decreaseStockIfEnough(id, 1)

        assertEquals(ProductAvailability.SOLD_OUT, assertNotNull(products.findById(id)).availability)
    }

    private fun raceToDecrease(
        id: Long,
        attempts: Int,
        quantity: Int,
    ): Int {
        val succeeded = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(attempts)
        val pool = Executors.newFixedThreadPool(attempts)
        try {
            repeat(attempts) {
                pool.submit {
                    // 모두 같은 순간에 출발시킨다. 순차 실행이면 경합이 재현되지 않는다.
                    start.await()
                    if (products.decreaseStockIfEnough(id, quantity)) {
                        succeeded.incrementAndGet()
                    }
                    done.countDown()
                }
            }
            start.countDown()
            check(done.await(10, TimeUnit.SECONDS)) { "차감 시도가 제한 시간 안에 끝나지 않았다" }
        } finally {
            pool.shutdownNow()
        }
        return succeeded.get()
    }

    private fun stockOf(id: Long): Int = assertNotNull(products.findById(id)).stockQuantity.value

    private fun onSaleProduct(stock: Int): Long {
        val product =
            registrationService.register(
                RegisterProductCommand("동시성 상품", "설명", 10_000, ProductCategory.ETC, stock),
            )
        val id = requireNotNull(product.id)
        managementService.startSelling(id)
        return id
    }
}
