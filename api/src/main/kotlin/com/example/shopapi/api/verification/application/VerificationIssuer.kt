package com.example.shopapi.api.verification.application

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.TokenGenerator
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.DuplicateEmailException
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.InvalidVerificationTokenException
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

        verificationRepository.deleteUnverifiedByEmail(email)

        return verificationRepository.save(
            EmailVerification.issue(
                verificationId = VerificationId.of(tokenGenerator.generate()),
                token = VerificationToken.of(tokenGenerator.generate()),
                email = email,
                now = timeProvider.now(),
            ),
        )
    }

    /**
     * 메일 링크 클릭. 이미 인증된 건이면 멱등하게 성공한다.
     *
     * 조회 실패를 `VERIFICATION_NOT_FOUND` 가 아니라 토큰 오류로 다룬다. 이 엔드포인트가
     * 받는 것은 토큰이고, 그것으로 아무것도 찾지 못했다면 토큰이 유효하지 않은 것이다.
     * 404 로 내보내면 verificationId 조회 실패와 구분되지 않는다.
     */
    @Transactional
    fun confirm(token: VerificationToken): EmailVerification {
        val verification = verificationRepository.findByToken(token) ?: throw InvalidVerificationTokenException()
        return verificationRepository.save(verification.verify(timeProvider.now()))
    }

    @Transactional(readOnly = true)
    fun statusOf(verificationId: VerificationId): EmailVerificationStatus {
        val verification =
            verificationRepository.findByVerificationId(verificationId) ?: throw VerificationNotFoundException()
        return verification.statusAt(timeProvider.now())
    }
}
