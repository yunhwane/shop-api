package com.example.shopapi.api.auth.application

import com.example.shopapi.api.support.AbuseGuard
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
    private val abuseGuard: AbuseGuard,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: AuthTokenIssuer,
    private val timeProvider: TimeProvider,
) {
    /**
     * 실패 경로에서 비교할 가짜 해시.
     *
     * 아이디가 없다고 바로 반환하면 bcrypt 를 건너뛰어 응답이 눈에 띄게 빨라진다.
     * 본문이 같아도 시간 차이만으로 아이디 존재를 알아낼 수 있다.
     */
    private val decoyPassword: EncodedPassword by lazy { passwordEncoder.encode(DECOY_PASSWORD) }

    @Transactional
    fun login(command: LoginCommand): IssuedTokens {
        // 해싱 전에 센다. 실패한 뒤에 세면 동시 요청이 모두 통과해 비용을 다 치른다.
        abuseGuard.guardLoginAttempt(command.clientIp)
        // 형식 위반이어도 400 이 아니라 401 로 돌려보낸다. 로그인에서 400 과 401 이 갈리면
        // 그 차이만으로 아이디 규칙과 존재 여부를 좁혀 갈 수 있다.
        val userId = parseOrNull { UserId.of(command.userId) }
        val rawPassword = parseOrNull { RawPassword.of(command.password) }
        val user = userId?.let(users::findByUserId)

        // 어느 실패 경로를 타든 해시 비교를 정확히 한 번 치른다. 형식 위반만 비교를
        // 건너뛰면, 응답 시간으로 "형식이 틀렸다"와 "없는 아이디다"가 갈린다.
        val matches =
            passwordEncoder.matches(
                rawPassword ?: DECOY_PASSWORD,
                user?.password ?: decoyPassword,
            )
        if (user == null || rawPassword == null || !matches) {
            throw InvalidCredentialsException()
        }

        // 비밀번호가 맞은 뒤에 본다. 먼저 보면 비밀번호를 모르는 사람에게도 계정 상태가 드러난다.
        user.ensureCanLogIn()

        // 성공했으니 앞에서 센 것을 되돌린다. 여러 사람이 한 IP 를 쓰는 환경에서
        // 성공까지 세면 아무도 공격하지 않았는데 한도가 찬다.
        abuseGuard.forgiveLogin(command.clientIp)

        val id = requireNotNull(user.id) { "저장된 회원이어야 한다" }
        return tokenIssuer.issueFor(id, timeProvider.now())
    }

    private fun <T> parseOrNull(build: () -> T): T? =
        try {
            build()
        } catch (e: InvalidValueException) {
            null
        }

    private companion object {
        val DECOY_PASSWORD = RawPassword.of("decoyPassword1")
    }
}

data class LoginCommand(
    val userId: String,
    val password: String,
    val clientIp: String,
)
