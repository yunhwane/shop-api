package com.example.shopapi.api

import com.example.shopapi.api.maintenance.ExpiredDataCleaner
import com.example.shopapi.core.domain.auth.RefreshToken
import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import com.example.shopapi.core.domain.verification.EmailVerification
import com.example.shopapi.core.domain.verification.VerificationId
import com.example.shopapi.core.domain.verification.VerificationToken
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 만료 데이터 정리(ADR 0010).
 *
 * 스케줄러가 스스로 도는 것을 기다리지 않고 직접 부른다. 검증하려는 것은 주기가 아니라
 * 무엇을 지우고 무엇을 남기는가다.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "mail.provider=log",
        // 테스트가 도는 동안 스케줄러가 끼어들지 않게 한다.
        "maintenance.cleanup.initial-delay=1h",
    ],
)
class ExpiredDataCleanupTest(
    @param:Autowired private val cleaner: ExpiredDataCleaner,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val refreshTokens: RefreshTokenRepository,
) {
    private val longAgo = Instant.parse("2020-01-01T00:00:00Z")

    @Test
    fun `기한이 지난 인증은 지우고 살아 있는 것은 남긴다`() {
        val expired = saveVerification("cleanup-expired", expiresAt = longAgo)
        val alive = saveVerification("cleanup-alive", expiresAt = Instant.now().plusSeconds(3600))

        cleaner.clean()

        assertNull(verifications.findByToken(expired), "기한이 지난 인증은 지워져야 한다")
        assertNotNull(verifications.findByToken(alive), "아직 유효한 인증은 남아야 한다")
    }

    /**
     * 소비된 행을 회전 직후에 지우면 재사용 탐지가 무너진다. 탈취범이 옛 토큰을 제시했을 때
     * "재사용"이 아니라 "없는 토큰"으로 보여 유출 신호를 놓친다(ADR 0008).
     *
     * 기준이 만료 하나여야 하는 이유다.
     */
    @Test
    fun `소비됐지만 기한이 남은 리프레시 토큰은 남긴다`() {
        val consumedButAlive =
            refreshTokens.save(
                RefreshToken(
                    id = null,
                    userId = 4242L,
                    tokenHash = "cleanup-consumed-alive",
                    expiresAt = Instant.now().plusSeconds(3600),
                    usedAt = Instant.now(),
                    createdAt = Instant.now(),
                ),
            )

        cleaner.clean()

        assertNotNull(
            refreshTokens.findByTokenHash(consumedButAlive.tokenHash),
            "소비됐어도 기한이 남았다면 재사용 탐지를 위해 남아야 한다",
        )
    }

    @Test
    fun `기한이 지난 리프레시 토큰은 지운다`() {
        refreshTokens.save(
            RefreshToken.issue(
                userId = 4243L,
                tokenHash = "cleanup-expired-token",
                expiresAt = longAgo,
                now = longAgo,
            ),
        )

        cleaner.clean()

        assertNull(refreshTokens.findByTokenHash("cleanup-expired-token"))
    }

    private fun saveVerification(
        token: String,
        expiresAt: Instant,
    ): VerificationToken {
        val verificationToken = VerificationToken.of(token)
        verifications.save(
            EmailVerification(
                id = null,
                verificationId = VerificationId.of("vid-$token"),
                token = verificationToken,
                email = Email.of("$token@example.com"),
                expiresAt = expiresAt,
                verifiedAt = null,
                consumedAt = null,
                createdAt = longAgo,
            ),
        )
        return verificationToken
    }
}
