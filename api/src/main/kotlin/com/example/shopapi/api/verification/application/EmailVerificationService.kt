package com.example.shopapi.api.verification.application

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.VerificationMailer
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationToken
import com.example.shopapi.core.enums.EmailVerificationStatus
import org.springframework.stereotype.Service

/**
 * 이메일 인증 유스케이스.
 *
 * **이 클래스에는 `@Transactional` 이 없다.** 트랜잭션은 [VerificationIssuer] 안에서
 * 열리고 닫히며, 메일은 그 커밋이 끝난 뒤에 나간다. 트랜잭션 안에서 보내면 이후 롤백되어도
 * 메일은 이미 나가 있기 때문이다.
 *
 * 커밋 후 발송을 `@TransactionalEventListener` 대신 **동기 호출**로 둔 이유가 있다.
 * 이벤트로 보내면 발송이 실패해도 사용자는 이미 201 을 받은 뒤라, 오지 않는 메일을
 * 무한정 기다리게 된다. 동기로 두면 실패가 502 로 그대로 보인다. 이때 인증 레코드는
 * 커밋된 채 남지만 아무도 쓰지 않고 만료되며, 재요청 시 어차피 정리된다.
 */
@Service
class EmailVerificationService(
    private val issuer: VerificationIssuer,
    private val mailer: VerificationMailer,
) {
    fun request(rawEmail: String): EmailVerification {
        val verification = issuer.issue(Email.of(rawEmail))
        mailer.sendVerification(verification.email, verification.token)
        return verification
    }

    fun confirm(rawToken: String) {
        issuer.confirm(VerificationToken.of(rawToken))
    }

    fun statusOf(rawVerificationId: String): EmailVerificationStatus =
        issuer.statusOf(VerificationId.of(rawVerificationId))
}
