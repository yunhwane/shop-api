package com.example.shopapi.core.domain.auth

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RefreshTokenTest {
    private val issuedAt = Instant.parse("2026-09-01T00:00:00Z")
    private val expiresAt = issuedAt.plusSeconds(3600)

    private fun token(usedAt: Instant? = null) =
        RefreshToken(
            id = 1L,
            userId = 7L,
            tokenHash = "hash",
            expiresAt = expiresAt,
            usedAt = usedAt,
            createdAt = issuedAt,
        )

    @Test
    fun `기한 안의 미사용 토큰은 쓸 수 있다`() {
        token().ensureUsable(issuedAt.plusSeconds(10))
    }

    @Test
    fun `이미 소비된 토큰은 재사용으로 본다`() {
        assertFailsWith<RefreshTokenReusedException> {
            token(usedAt = issuedAt.plusSeconds(10)).ensureUsable(issuedAt.plusSeconds(20))
        }
    }

    /**
     * 검사 순서가 뒤집히면 오래전에 새어 나간 토큰 하나가 영구적인 "전 기기 로그아웃" 버튼이 된다.
     * 소비된 행은 지워지지 않고 남으므로, 공격자가 원할 때마다 피해자를 끊을 수 있다.
     */
    @Test
    fun `기한이 지난 토큰은 소비 여부와 무관하게 그냥 무효다`() {
        val afterExpiry = expiresAt.plusSeconds(1)

        assertFailsWith<RefreshTokenExpiredException> { token().ensureUsable(afterExpiry) }
        assertFailsWith<RefreshTokenExpiredException> {
            token(usedAt = issuedAt.plusSeconds(10)).ensureUsable(afterExpiry)
        }
    }

    @Test
    fun `재사용 예외는 폐기 대상 사용자를 알려준다`() {
        val exception =
            assertFailsWith<RefreshTokenReusedException> {
                token(usedAt = issuedAt).ensureUsable(issuedAt.plusSeconds(10))
            }

        kotlin.test.assertEquals(7L, exception.userId)
    }

    @Test
    fun `문자열 표현에 해시가 담기지 않는다`() {
        kotlin.test.assertEquals(false, token().toString().contains("hash"))
    }
}
