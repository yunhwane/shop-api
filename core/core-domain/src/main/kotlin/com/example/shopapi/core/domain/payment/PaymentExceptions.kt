package com.example.shopapi.core.domain.payment

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

class PaymentNotFoundException : DomainException(ErrorCode.PAYMENT_NOT_FOUND)

/** 클라이언트가 보낸 금액이 [Payment.amount] 와 다르다(ADR 0017) */
class PaymentAmountMismatchException : DomainException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)

/** 이미 완료됐거나 실패해 다시 확정할 수 없는 결제 시도다 */
class PaymentNotReadyException : DomainException(ErrorCode.PAYMENT_NOT_READY)

/** `DONE` 이 아니라서 취소할 수 없는 결제 시도다(ADR 0018) */
class PaymentNotCancellableException : DomainException(ErrorCode.PAYMENT_NOT_CANCELLABLE)

/**
 * Toss 승인 호출이 실패했다.
 *
 * 원인은 [cause] 로만 들고 다닌다. Toss 오류 응답에 담긴 사유를 그대로 클라이언트에
 * 노출하지 않는다 - [com.example.shopapi.core.domain.common.MailSendException] 과 같은 이유다.
 */
class PaymentConfirmFailedException(
    override val cause: Throwable? = null,
) : DomainException(ErrorCode.PAYMENT_CONFIRM_FAILED)

/** Toss 취소 호출이 실패했다. [PaymentConfirmFailedException] 과 같은 이유로 원인은 노출하지 않는다(ADR 0018) */
class PaymentCancelFailedException(
    override val cause: Throwable? = null,
) : DomainException(ErrorCode.PAYMENT_CANCEL_FAILED)
