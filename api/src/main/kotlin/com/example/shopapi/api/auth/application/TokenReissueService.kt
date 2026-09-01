package com.example.shopapi.api.auth.application

import com.example.shopapi.core.domain.auth.InvalidRefreshTokenException
import com.example.shopapi.core.domain.auth.RefreshTokenReusedException
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.TokenHasher
import com.example.shopapi.core.domain.port.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리프레시 토큰 회전과 로그아웃(ADR 0008).
 */
@Service
class TokenReissueService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val tokenHasher: TokenHasher,
    private val tokenIssuer: AuthTokenIssuer,
    private val timeProvider: TimeProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * `noRollbackFor` 가 이 기능의 핵심이다.
     *
     * 재사용을 탐지하면 해당 사용자의 토큰을 전부 지우고 예외를 던진다. 그런데 예외가
     * 트랜잭션을 롤백시키면 **방금 실행한 폐기까지 함께 되돌아간다.** 유출을 탐지해 놓고
     * 아무것도 하지 않은 것과 같아지고, 탈취범의 토큰은 그대로 살아남는다.
     *
     * 이 경로에서 롤백할 것은 없다 - 소비 처리는 예외 전에 실패했고 남은 것은 폐기뿐이다.
     */
    @Transactional(noRollbackFor = [RefreshTokenReusedException::class])
    fun reissue(rawRefreshToken: String): IssuedTokens {
        val stored =
            refreshTokens.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                ?: throw InvalidRefreshTokenException()

        val now = timeProvider.now()
        try {
            stored.ensureUsable(now)
        } catch (e: RefreshTokenReusedException) {
            revokeAllOf(e.userId)
            throw e
        }

        // 여기까지의 검사는 흔한 실패를 빠르게 걸러낼 뿐이다. 같은 토큰으로 동시에 들어온
        // 요청은 둘 다 이 지점을 통과한다. 실제 소비는 DB 가 한 번만 성공시킨다.
        if (!refreshTokens.markUsedIfUnused(requireNotNull(stored.id) { "저장된 토큰이어야 한다" }, now)) {
            revokeAllOf(stored.userId)
            throw RefreshTokenReusedException(stored.userId)
        }

        // 계정 상태를 여기서 다시 본다. 로그인 때만 보면 정지된 뒤에도 재발급으로
        // 접근이 유지되고, 회전이 만료를 매번 갱신해 사실상 끊기지 않는 세션이 된다.
        val user = users.findById(stored.userId) ?: throw InvalidRefreshTokenException()
        user.ensureCanLogIn()

        return tokenIssuer.issueFor(stored.userId, now)
    }

    private fun revokeAllOf(userId: Long) {
        // 어느 쪽이 탈취범인지 알 수 없으므로 양쪽 모두 끊는다.
        log.warn("리프레시 토큰 재사용 탐지. 해당 사용자의 토큰을 모두 폐기한다. userId={}", userId)
        refreshTokens.deleteAllByUserId(userId)
    }

    /**
     * 로그아웃. 제시된 토큰만 지운다.
     *
     * 없는 토큰이어도 성공으로 둔다. 로그아웃은 결과가 같으면 되는 요청이고,
     * 실패로 알려 주면 토큰의 존재 여부를 확인하는 통로가 된다.
     */
    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokens.deleteByTokenHash(tokenHasher.hash(rawRefreshToken))
    }
}
