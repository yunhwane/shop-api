package com.example.shopapi.api.user.application

import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.PasswordEncoder
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.DuplicateUserIdException
import com.example.shopapi.core.domain.user.RawPassword
import com.example.shopapi.core.domain.user.User
import com.example.shopapi.core.domain.user.UserId
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원가입 유스케이스(ADR 0003).
 *
 * 이메일은 요청이 아니라 인증 레코드에서 가져온다. 인증한 주소와 다른 주소로 가입하는
 * 경로를 원천 차단한다(ADR 0002).
 */
@Service
class SignUpService(
    private val userRepository: UserRepository,
    private val verificationRepository: EmailVerificationRepository,
    private val passwordEncoder: PasswordEncoder,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun signUp(command: SignUpCommand): User {
        // 값 객체 생성이 곧 형식 검증이다. 실패하면 InvalidValueException 이 나간다.
        val verificationId = VerificationId.of(command.verificationId)
        val userId = UserId.of(command.userId)
        val rawPassword = RawPassword.of(command.password)

        val verification =
            verificationRepository.findByVerificationId(verificationId) ?: throw VerificationNotFoundException()

        val now = timeProvider.now()
        // 미인증 / 만료 / 이미 사용됨을 도메인이 판정한다. 소비 처리하지 않으면
        // 인증 하나로 계정을 여러 개 만들 수 있다.
        val consumed = verification.consume(now)

        // 이 사전 검사가 중복을 막는 것이 아니다. 동시 요청은 둘 다 통과한다.
        // 실제 방어선은 DB 유니크 제약이고, 어댑터가 위반을 도메인 예외로 번역한다(ADR 0005).
        // 여기서는 평시에 정확한 메시지를 주는 역할만 한다.
        if (userRepository.existsByUserId(userId)) throw DuplicateUserIdException(userId)

        verificationRepository.save(consumed)

        return userRepository.save(
            User.register(
                userId = userId,
                email = consumed.email,
                password = passwordEncoder.encode(rawPassword),
                now = now,
            ),
        )
    }
}

data class SignUpCommand(
    val verificationId: String,
    val userId: String,
    val password: String,
)
