package com.example.shopapi.api.user

import com.example.shopapi.api.user.application.SignUpCommand
import com.example.shopapi.api.user.application.SignUpService
import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.PasswordEncoder
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.DuplicateUserIdException
import com.example.shopapi.core.domain.user.EncodedPassword
import com.example.shopapi.core.domain.user.RawPassword
import com.example.shopapi.core.domain.user.User
import com.example.shopapi.core.domain.user.UserId
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationAlreadyUsedException
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationNotCompletedException
import com.example.shopapi.core.domain.verification.VerificationNotFoundException
import com.example.shopapi.core.domain.verification.VerificationToken
import com.example.shopapi.core.enums.EmailVerificationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 회원가입 유스케이스. 포트를 인메모리 가짜로 대체해 Spring 컨텍스트 없이 검증한다.
 */
class SignUpServiceTest {
    private val now = Instant.parse("2026-09-01T00:00:00Z")
    private val email = Email.of("user@example.com")
    private val verificationId = VerificationId.of("verification-1")
    private val token = VerificationToken.of("token-1")

    private val users = FakeUserRepository()
    private val verifications = FakeEmailVerificationRepository()
    private val service =
        SignUpService(
            userRepository = users,
            verificationRepository = verifications,
            passwordEncoder = ReversiblePasswordEncoder,
            timeProvider = TimeProvider { now },
        )

    private fun command(userId: String = "alice01") =
        SignUpCommand(verificationId = verificationId.value, userId = userId, password = "password1")

    private fun givenVerified(): EmailVerification =
        verifications.save(
            EmailVerification
                .issue(verificationId, token, email, now.minusSeconds(60))
                .verify(now.minusSeconds(30)),
        )

    @Test
    fun `인증을 마쳤으면 가입된다`() {
        givenVerified()

        val user = service.signUp(command())

        assertEquals(UserId.of("alice01"), user.userId)
        assertEquals(email, user.email)
    }

    /** 이메일은 요청이 아니라 인증 레코드에서 가져온다(ADR 0002). */
    @Test
    fun `이메일은 인증 레코드에서 가져온다`() {
        givenVerified()

        assertEquals(email, service.signUp(command()).email)
    }

    @Test
    fun `비밀번호를 평문으로 저장하지 않는다`() {
        givenVerified()

        val user = service.signUp(command())

        assertEquals(false, user.password.value.contains("password1"))
    }

    @Test
    fun `가입에 사용한 인증은 소비된다`() {
        givenVerified()

        service.signUp(command())

        val consumed = verifications.findByVerificationId(verificationId)
        assertEquals(EmailVerificationStatus.CONSUMED, consumed?.statusAt(now))
    }

    /** 이 규칙이 없으면 인증 한 번으로 계정을 여러 개 만들 수 있다. */
    @Test
    fun `같은 인증으로 두 번 가입할 수 없다`() {
        givenVerified()
        service.signUp(command())

        assertFailsWith<VerificationAlreadyUsedException> { service.signUp(command("alice02")) }
    }

    @Test
    fun `인증을 마치지 않았으면 가입할 수 없다`() {
        verifications.save(EmailVerification.issue(verificationId, token, email, now))

        assertFailsWith<VerificationNotCompletedException> { service.signUp(command()) }
    }

    @Test
    fun `인증 정보가 없으면 가입할 수 없다`() {
        assertFailsWith<VerificationNotFoundException> { service.signUp(command()) }
    }

    @Test
    fun `이미 쓰이는 아이디로는 가입할 수 없다`() {
        givenVerified()
        users.save(
            User.register(UserId.of("alice01"), Email.of("other@example.com"), EncodedPassword.of("x"), now),
        )

        assertFailsWith<DuplicateUserIdException> { service.signUp(command()) }
    }

    @Test
    fun `형식에 맞지 않는 아이디는 거부한다`() {
        givenVerified()

        assertFailsWith<InvalidValueException> { service.signUp(command(userId = "ab")) }
    }
}

/** 해싱이 실제로 일어났는지만 보면 되므로 되돌릴 수 있는 가짜를 쓴다. bcrypt 는 느리다. */
private object ReversiblePasswordEncoder : PasswordEncoder {
    private const val PREFIX = "encoded:"

    override fun encode(raw: RawPassword): EncodedPassword = EncodedPassword.of(PREFIX + raw.value.reversed())

    override fun matches(
        raw: RawPassword,
        encoded: EncodedPassword,
    ): Boolean = encoded.value == PREFIX + raw.value.reversed()
}

private class FakeUserRepository : UserRepository {
    private val stored = mutableListOf<User>()
    private var sequence = 0L

    override fun save(user: User): User {
        val saved =
            User(
                id = user.id ?: ++sequence,
                userId = user.userId,
                email = user.email,
                password = user.password,
                status = user.status,
                createdAt = user.createdAt,
            )
        stored.add(saved)
        return saved
    }

    override fun findById(id: Long): User? = stored.find { it.id == id }

    override fun findByUserId(userId: UserId): User? = stored.find { it.userId == userId }

    override fun existsByUserId(userId: UserId): Boolean = findByUserId(userId) != null

    override fun existsByEmail(email: Email): Boolean = stored.any { it.email == email }
}

private class FakeEmailVerificationRepository : EmailVerificationRepository {
    private val stored = mutableMapOf<String, EmailVerification>()

    override fun save(verification: EmailVerification): EmailVerification {
        stored[verification.verificationId.value] = verification
        return verification
    }

    override fun findByVerificationId(verificationId: VerificationId): EmailVerification? = stored[verificationId.value]

    override fun findByToken(token: VerificationToken): EmailVerification? = stored.values.find { it.token == token }

    override fun deleteUnverifiedByEmail(email: Email) {
        stored.values.removeIf { it.email == email && it.verifiedAt == null }
    }

    override fun deleteUnusableAsOf(now: Instant): Int {
        val before = stored.size
        stored.values.removeIf { now >= it.expiresAt && it.verifiedAt == null }
        return before - stored.size
    }
}
