package com.example.shopapi.core.domain.common

import com.example.shopapi.core.enums.ErrorCode

/**
 * 메일 게이트웨이 호출이 실패했다.
 *
 * 원인 예외는 [cause] 로만 들고 다니고 메시지에 담지 않는다. 외부 API 의 응답 본문에
 * 자격증명이나 수신자 정보가 섞여 있을 수 있어 그대로 클라이언트에 노출하면 안 된다.
 */
class MailSendException(
    override val cause: Throwable? = null,
) : DomainException(ErrorCode.MAIL_SEND_FAILED)
