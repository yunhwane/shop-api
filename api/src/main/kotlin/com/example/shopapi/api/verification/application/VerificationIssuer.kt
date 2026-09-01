package com.example.shopapi.api.verification.application

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.TokenGenerator
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.DuplicateEmailException
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationNotFoundException
import com.example.shopapi.core.domain.verification.VerificationToken
import com.example.shopapi.core.enums.EmailVerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증 레코드의 트랜잭션 경계.
 *
 * 메일 발송은 여기 없다. [EmailVerificationService] 가 커밋 이후에 보낸다 —
 * 이유는 그쪽 주석을 참고한다.
 */
@Service
class VerificationIssuer(
    private val userRepository: UserRepository,
    private val verificationRepository: EmailVerificationRepository,
    private val tokenGenerator: TokenGenerator,
    private val timeProvider: TimeProvider,
) {
    /**
     * 인증을 발급한다.
     *
     * 이미 가입된 이메일이면 409 로 명확히 알린다. 가입 여부가 노출되지만, 가입 단계에서
     * 어차피 드러나는 정보이므로 감추는 실익이 없다고 판단했다(ADR 0005).
     */
    @Transactional
    fun issue(email: Email): EmailVerification {
        if (userRepository.existsByEmail(email)) throw DuplicateEmailException()

        // 이전 링크를 무효화한다. 남겨 두면 오래된 메일로도 인증이 되어,
        // 사용자가 어느 메일을 열었는지에 따라 결과가 달라진다.
        verificationRepository.deleteUnconsumedByEmail(email)

        return verificationRepository.save(
            EmailVerification.issue(
                verificationId = VerificationId.of(tokenGenerator.generate()),
                token = VerificationToken.of(tokenGenerator.generate()),
                email = email,
                now = timeProvider.now(),
            ),
        )
    }

    /** 메일 링크 클릭. 이미 인증된 건이면 멱등하게 성공한다. */
    @Transactional
    fun confirm(token: VerificationToken): EmailVerification {
        val verification = verificationRepository.findByToken(token) ?: throw VerificationNotFoundException()
        return verificationRepository.save(verification.verify(token, timeProvider.now()))
    }

    @Transactional(readOnly = true)
    fun statusOf(verificationId: VerificationId): EmailVerificationStatus {
        val verification =
            verificationRepository.findByVerificationId(verificationId) ?: throw VerificationNotFoundException()
        return verification.statusAt(timeProvider.now())
    }
}
