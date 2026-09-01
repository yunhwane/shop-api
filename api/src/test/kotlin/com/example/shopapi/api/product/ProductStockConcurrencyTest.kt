package com.example.shopapi.api.product

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.port.ProductRepository
import com.example.shopapi.core.enums.ProductAvailability
import com.example.shopapi.core.enums.ProductCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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
    @param:Autowired transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)

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

    /**
     * 카탈로그 저장이 재고를 되돌려 놓는 경로를 막는다.
     *
     * 상품을 읽어 둔 트랜잭션이 가격을 고쳐 저장하는 사이에 주문이 재고를 줄이면,
     * 저장이 전체 컬럼을 쓰면서 **읽어 뒀던 옛 재고를 되쓴다.** 조건부 원자 갱신으로
     * 막은 초과 판매가 이 옆문으로 들어온다(ADR 0014).
     */
    @Test
    fun `가격을 고쳐도 그 사이 팔린 재고가 되살아나지 않는다`() {
        val id = onSaleProduct(stock = 1)

        transactions.execute {
            // 재고 1 을 읽어 둔다
            val product = assertNotNull(products.findById(id))
            assertEquals(1, product.stockQuantity.value)

            // 다른 트랜잭션에서 주문이 재고를 가져간다. 별도 스레드라 이 트랜잭션에 합류하지 않는다
            decreaseInAnotherTransaction(id)

            products.save(product.changePrice(Money.of(20_000), product.updatedAt))
        }

        assertEquals(0, stockOf(id), "가격 저장이 차감을 지웠다")
        assertEquals(20_000, assertNotNull(products.findById(id)).price.amount)
    }

    /**
     * 판매 상태도 같은 조건절에서 본다.
     *
     * 호출자가 미리 읽어 확인하는 것으로는 막지 못한다. 읽은 뒤 차감하기까지의 사이에
     * 단종될 수 있고, 그때 상품은 팔리면 안 되는데 팔린다.
     */
    @Test
    fun `팔지 않는 상품은 재고가 줄지 않는다`() {
        val discontinued = onSaleProduct(stock = 5).also { managementService.discontinue(it) }
        val suspended = onSaleProduct(stock = 5).also { managementService.suspendSelling(it) }
        val draft = draftProduct(stock = 5)

        listOf(discontinued, suspended, draft).forEach { id ->
            assertEquals(false, products.decreaseStockIfEnough(id, 1), "상품 $id 는 팔리면 안 된다")
            assertEquals(5, stockOf(id))
        }
    }

    @Test
    fun `없는 상품의 재고는 되돌릴 것이 없다`() {
        assertEquals(false, products.increaseStock(999_999L, 1))
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
        val pool = Executors.newFixedThreadPool(attempts)
        try {
            val futures =
                (1..attempts).map {
                    pool.submit {
                        // 모두 같은 순간에 출발시킨다. 순차 실행이면 경합이 재현되지 않는다.
                        start.await()
                        if (products.decreaseStockIfEnough(id, quantity)) {
                            succeeded.incrementAndGet()
                        }
                    }
                }
            start.countDown()
            // 결과를 꺼내야 워커가 삼킨 예외가 드러난다. 세지 않고 기다리기만 하면
            // 커넥션 고갈 같은 진짜 원인이 "시간 안에 끝나지 않았다"로 가려진다.
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        return succeeded.get()
    }

    /** 스레드를 하나 쓴다. 호출 스레드에 트랜잭션이 열려 있어도 거기 합류하지 않아야 한다. */
    private fun decreaseInAnotherTransaction(id: Long) {
        val thread = Thread { products.decreaseStockIfEnough(id, 1) }
        thread.start()
        thread.join(10_000)
    }

    private fun stockOf(id: Long): Int = assertNotNull(products.findById(id)).stockQuantity.value

    private fun draftProduct(stock: Int): Long =
        requireNotNull(
            registrationService
                .register(RegisterProductCommand("동시성 상품", "설명", 10_000, ProductCategory.ETC, stock))
                .id,
        )

    private fun onSaleProduct(stock: Int): Long = draftProduct(stock).also { managementService.startSelling(it) }
}
