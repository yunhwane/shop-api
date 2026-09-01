package com.example.shopapi.api.maintenance

import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import com.example.shopapi.core.domain.port.TimeProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 기한이 지난 인증과 리프레시 토큰을 지운다(ADR 0010).
 *
 * 리프레시 토큰은 회전 때문에 로그인과 재발급마다 행이 하나씩 는다. 지우지 않으면
 * 활성 사용자 수가 아니라 사용 횟수에 비례해 자란다.
 *
 * **인스턴스마다 실행된다.** 여러 대로 늘리면 같은 삭제가 중복으로 돈다. `DELETE` 는
 * 멱등이라 결과는 같지만, 그때는 리더 선출이나 전용 실행기가 필요하다.
 */
@Component
@ConditionalOnProperty(
    prefix = "maintenance.cleanup",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ExpiredDataCleaner(
    private val verifications: EmailVerificationRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val timeProvider: TimeProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${maintenance.cleanup.initial-delay:5m}",
        fixedDelayString = "\${maintenance.cleanup.interval:1h}",
    )
    fun clean() {
        val now = timeProvider.now()
        val removedVerifications = verifications.deleteExpiredBefore(now)
        val removedTokens = refreshTokens.deleteExpiredBefore(now)

        if (removedVerifications > 0 || removedTokens > 0) {
            log.info(
                "만료 데이터 정리. email_verifications={} refresh_tokens={}",
                removedVerifications,
                removedTokens,
            )
        }
    }
}
