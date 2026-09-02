package com.example.shopapi.api.payment.application

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.order.Order
import com.example.shopapi.core.domain.order.OrderNotFoundException
import com.example.shopapi.core.domain.payment.PaymentConfirmFailedException
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.PaymentNotFoundException
import com.example.shopapi.core.domain.payment.PaymentNotReadyException
import com.example.shopapi.core.domain.payment.TossOrderId
import com.example.shopapi.core.domain.port.OrderRepository
import com.example.shopapi.core.domain.port.PaymentGateway
import com.example.shopapi.core.domain.port.PaymentRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.enums.OrderStatus
import com.example.shopapi.core.enums.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 확정.
 *
 * Toss 승인 호출 앞에 세 방어선을 둔다 - 금액 대조([verifyAmount][com.example.shopapi.core.domain.payment.Payment.verifyAmount]),
 * 이미 `DONE` 인 시도에 대한 멱등 처리(ADR 0017), 그리고 같은 주문의 다른 시도가 이미
 * Toss 승인을 진행 중이면 이 시도는 아예 Toss 를 부르지 않는 선점(ADR 0019)이다.
 *
 * [PaymentConfirmFailedException] 을 `noRollbackFor` 로 막아 둔다. Toss 가 거절했을 때
 * [markFailedIfReady][com.example.shopapi.core.domain.port.PaymentRepository.markFailedIfReady]
 * 로 남긴 `FAILED` 기록과
 * [releaseClaimedPayment][com.example.shopapi.core.domain.port.OrderRepository.releaseClaimedPayment]
 * 로 놓아준 선점까지 롤백되면, `TokenReissueService` 가 `noRollbackFor` 없이 리프레시
 * 토큰 폐기를 롤백당했던 것과 같은 모양으로 이 주문의 모든 후속 결제 시도가 영영 막힌다.
 */
@Service
class ConfirmPaymentService(
    private val orders: OrderRepository,
    private val payments: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val timeProvider: TimeProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(noRollbackFor = [PaymentConfirmFailedException::class])
    fun confirm(
        buyerId: Long,
        orderId: Long,
        command: ConfirmPaymentCommand,
    ): Order {
        val order = orders.findById(orderId) ?: throw OrderNotFoundException()
        if (order.buyerId != buyerId) throw OrderNotFoundException()

        val tossOrderId = TossOrderId.of(command.tossOrderId)
        val payment =
            payments.findByOrderIdAndTossOrderId(orderId, tossOrderId) ?: throw PaymentNotFoundException()
        val paymentId = requireNotNull(payment.id)

        if (payment.status == PaymentStatus.DONE) {
            // 이중 클릭·네트워크 재시도로 온 재확정. 같은 결제 키면 이미 성공한 결과를
            // 그대로 돌려준다 - Toss 를 다시 부르면 승인된 paymentKey 라 거부당한다.
            // order 는 이 메서드 맨 위에서 막 읽었으므로 이미 그 확정을 반영한 상태다.
            if (payment.paymentKey?.value == command.paymentKey) {
                return order
            }
            throw PaymentNotReadyException()
        }
        if (payment.status != PaymentStatus.READY) {
            throw PaymentNotReadyException()
        }

        payment.verifyAmount(parseAmount(command.amount))
        order.ensurePayable()

        if (!orders.claimPaymentIfPlaced(orderId, paymentId, timeProvider.now())) {
            // 같은 주문의 다른 결제 시도가 이미 이 주문을 선점했다 - Toss 를 부르지
            // 않고 거절한다(ADR 0019).
            throw PaymentNotReadyException()
        }

        val paymentKey = PaymentKey.of(command.paymentKey)
        val confirmation =
            try {
                paymentGateway.confirm(paymentKey, tossOrderId, payment.amount)
            } catch (e: PaymentConfirmFailedException) {
                payments.markFailedIfReady(paymentId, timeProvider.now())
                orders.releaseClaimedPayment(orderId, paymentId, timeProvider.now())
                throw e
            }

        val now = timeProvider.now()
        if (!payments.markDoneIfReady(paymentId, paymentKey, confirmation.approvedAt, now)) {
            // 동시에 들어온 다른 확정 요청이 먼저 DONE 으로 전이시켰다. claimPaymentIfPlaced
            // 가 이 주문을 이 결제 시도에게 독점시켰으므로 정상적으로는 일어나지 않는다
            // (ADR 0019). Toss 는 이미 승인했으니 여기서 실패로 답하지 않고 그대로
            // 진행한다 - cancelIfPlaced 와 같은 조건부 원자 갱신 규약이다(ADR 0016).
            log.warn("이미 처리된 결제 시도에 대한 뒤늦은 확정. paymentId={}", paymentId)
        }

        if (!orders.markPaidIfPlaced(orderId, now)) {
            val current = orders.findById(orderId)
            if (current?.status != OrderStatus.PAID) {
                // 돈은 실제로 받았는데 주문은 PAID 가 아니다 - 취소와 확정이 겹친
                // 좁은 경합이다(ADR 0017). 조용히 넘기지 않고 수동 정산이 필요함을 남긴다.
                log.error(
                    "결제는 승인됐지만 주문을 PAID 로 전이하지 못했다. 수동 정산이 필요하다. " +
                        "orderId={}, paymentId={}, orderStatus={}",
                    orderId,
                    paymentId,
                    current?.status,
                )
            }
            return requireNotNull(current)
        }

        return order.pay(now)
    }

    /** [Money.of] 는 실패 시 필드명을 항상 `price` 로 담는다 - 이 메서드의 요청 필드인 `amount` 로 바꿔 던진다 */
    private fun parseAmount(raw: Long): Money =
        try {
            Money.of(raw)
        } catch (e: InvalidValueException) {
            throw InvalidValueException("amount", e.reason)
        }
}
