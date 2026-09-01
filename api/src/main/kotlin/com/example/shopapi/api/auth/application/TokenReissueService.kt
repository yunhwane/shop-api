package com.example.shopapi.api.auth.application

import com.example.shopapi.core.domain.auth.InvalidRefreshTokenException
import com.example.shopapi.core.domain.auth.RefreshTokenReusedException
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import com.example.shopapi.core.domain.port.TimeProvider
import com.example.shopapi.core.domain.port.TokenHasher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리프레시 토큰 회전과 로그아웃(ADR 0008).
 */
@Service
class TokenReissueService(
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
        val used =
            try {
                stored.use(now)
            } catch (e: RefreshTokenReusedException) {
                // 이미 쓴 토큰이 다시 왔다는 것은 어딘가로 새어 나갔다는 뜻이다.
                // 어느 쪽이 탈취범인지 알 수 없으므로 양쪽 모두 끊는다.
                log.warn("리프레시 토큰 재사용 탐지. 해당 사용자의 토큰을 모두 폐기한다. userId={}", e.userId)
                refreshTokens.deleteAllByUserId(e.userId)
                throw e
            }

        refreshTokens.save(used)
        return tokenIssuer.issueFor(used.userId, now)
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
