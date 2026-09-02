package com.example.shopapi.client.payment.toss

import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.domain.payment.PaymentConfirmFailedException
import com.example.shopapi.core.domain.payment.PaymentKey
import com.example.shopapi.core.domain.payment.TossOrderId
import com.example.shopapi.core.domain.port.PaymentConfirmation
import com.example.shopapi.core.domain.port.PaymentGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * Toss Payments 결제 승인 API 어댑터.
 *
 * `Authorization` 은 시크릿 키를 Basic 인증의 아이디 자리에 넣고 비밀번호는 비운다 -
 * Toss 가 요구하는 인증 방식이다.
 */
@Component
@ConditionalOnProperty(prefix = "payment.toss", name = ["provider"], havingValue = "toss", matchIfMissing = true)
internal class TossPaymentGateway(
    private val properties: TossPaymentProperties,
    // 파라미터 이름이 빈 이름(tossRestClient)과 같아야 한다 - RestClient 빈이
    // mailRestClient 와 둘이라 타입만으로는 하나로 정해지지 않는다.
    private val tossRestClient: RestClient,
) : PaymentGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun confirm(
        paymentKey: PaymentKey,
        tossOrderId: TossOrderId,
        amount: Money,
    ): PaymentConfirmation {
        try {
            val response =
                tossRestClient
                    .post()
                    .uri(properties.confirmEndpoint)
                    .header("Authorization", "Basic ${encodedSecretKey()}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ConfirmRequest(paymentKey.value, tossOrderId.value, amount.amount))
                    .retrieve()
                    .body(ConfirmResponse::class.java)
                    ?: throw PaymentConfirmFailedException()
            return PaymentConfirmation(approvedAt = OffsetDateTime.parse(response.approvedAt).toInstant())
        } catch (e: RestClientException) {
            // Toss 오류 응답에는 거절 사유가 담기지만 클라이언트에 그대로 노출하지 않는다 -
            // MailSendException 이 응답 본문을 남기지 않는 것과 같은 이유다.
            log.error("Toss 결제 승인 실패. tossOrderId={}", tossOrderId.value, e)
            throw PaymentConfirmFailedException(e)
        } catch (e: DateTimeParseException) {
            // Toss 는 이미 승인했다 - 응답 파싱 실패를 승인 실패와 같은 결과로 다뤄
            // 이 결제 시도를 FAILED 로 확정 짓는다. 그대로 던지면 승인은 됐는데 아무
            // 기록도 안 남긴 채 500 으로만 끝난다.
            log.error("Toss 승인 응답의 approvedAt 을 해석하지 못했다. tossOrderId={}", tossOrderId.value, e)
            throw PaymentConfirmFailedException(e)
        }
    }

    private fun encodedSecretKey(): String =
        Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray())

    private data class ConfirmRequest(
        val paymentKey: String,
        val orderId: String,
        val amount: Long,
    )

    private data class ConfirmResponse(
        val approvedAt: String,
    )
}
