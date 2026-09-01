package com.example.shopapi.api.auth.application

import com.example.shopapi.core.domain.auth.InvalidCredentialsException
import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.port.PasswordEncoder
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.EncodedPassword
import com.example.shopapi.core.domain.user.RawPassword
import com.example.shopapi.core.domain.user.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로그인. 실패는 이유를 가리지 않고 하나로 응답한다(ADR 0008).
 */
@Service
class LoginService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: AuthTokenIssuer,
    private val timeProvider: TimeProvider,
) {
    /**
     * 사용자를 찾지 못했을 때 비교할 가짜 해시.
     *
     * 없는 아이디라고 바로 반환하면 bcrypt 를 건너뛰어 응답이 눈에 띄게 빨라진다.
     * 본문이 같아도 시간 차이만으로 아이디 존재를 알아낼 수 있다.
     */
    private val decoyPassword: EncodedPassword by lazy {
        passwordEncoder.encode(RawPassword.of("decoyPassword1"))
    }

    @Transactional
    fun login(command: LoginCommand): IssuedTokens {
        // 형식 위반도 400 이 아니라 401 로 돌려보낸다. 로그인에서 400 과 401 이 갈리면
        // 그 차이만으로 아이디 규칙과 존재 여부를 좁혀 갈 수 있다.
        val userId = asCredential { UserId.of(command.userId) }
        val rawPassword = asCredential { RawPassword.of(command.password) }

        val user = users.findByUserId(userId)
        if (user == null) {
            passwordEncoder.matches(rawPassword, decoyPassword)
            throw InvalidCredentialsException()
        }
        if (!passwordEncoder.matches(rawPassword, user.password)) {
            throw InvalidCredentialsException()
        }

        // 비밀번호가 맞은 뒤에 본다. 먼저 보면 비밀번호를 모르는 사람에게도 계정 상태가 드러난다.
        user.ensureCanLogIn()

        val id = requireNotNull(user.id) { "저장된 회원이어야 한다" }
        return tokenIssuer.issueFor(id, timeProvider.now())
    }

    private fun <T> asCredential(build: () -> T): T =
        try {
            build()
        } catch (e: InvalidValueException) {
            throw InvalidCredentialsException()
        }
}

data class LoginCommand(
    val userId: String,
    val password: String,
)
