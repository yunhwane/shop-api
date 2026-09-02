package com.example.shopapi.client.payment.toss

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import com.example.shopapi.core.domain.port.PaymentCancellation
import com.example.shopapi.core.domain.port.PaymentConfirmation
import com.example.shopapi.core.domain.port.PaymentGateway
import com.example.shopapi.core.domain.port.TimeProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 실제로 Toss 를 부르지 않고 항상 승인 성공을 돌려준다. `payment.toss.provider=fake` 로 켠다.
 *
 * 로컬 개발과 테스트용이다. Toss 시크릿 키 없이 결제 흐름 전체를 돌려볼 수 있다.
 * 운영 프로파일에서 이 값을 쓰지 않도록 주의한다.
 */
@Component
@ConditionalOnProperty(prefix = "payment.toss", name = ["provider"], havingValue = "fake")
internal class FakePaymentGateway(
    private val timeProvider: TimeProvider,
) : PaymentGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun confirm(
        paymentKey: PaymentKey,
        tossOrderId: TossOrderId,
        amount: Money,
    ): PaymentConfirmation {
        log.info("[Toss 승인 생략] tossOrderId={} amount={}", tossOrderId.value, amount)
        return PaymentConfirmation(approvedAt = timeProvider.now())
    }

    override fun cancel(
        paymentKey: PaymentKey,
        cancelReason: String,
    ): PaymentCancellation {
        log.info("[Toss 취소 생략] paymentKey={} cancelReason={}", paymentKey, cancelReason)
        return PaymentCancellation(canceledAt = timeProvider.now())
    }
}
