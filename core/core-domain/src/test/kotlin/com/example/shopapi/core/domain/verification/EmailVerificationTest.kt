package com.example.shopapi.core.domain.verification

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.enums.EmailVerificationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class EmailVerificationTest {
    private val email = Email.of("user@example.com")
    private val verificationId = VerificationId.of("verification-1")
    private val token = VerificationToken.of("token-1")
    private val issuedAt = Instant.parse("2026-09-01T00:00:00Z")

    private fun issued() = EmailVerification.issue(verificationId, token, email, issuedAt)

    @Test
    fun `발급 직후에는 인증 대기 상태다`() {
        assertEquals(EmailVerificationStatus.PENDING, issued().statusAt(issuedAt))
    }

    @Test
    fun `링크 기한이 지나면 만료된다`() {
        val afterExpiry = issuedAt + EmailVerification.TIME_TO_LIVE

        assertEquals(EmailVerificationStatus.EXPIRED, issued().statusAt(afterExpiry))
    }

    @Test
    fun `인증하면 인증 완료 상태가 되고 시각이 기록된다`() {
        val at = issuedAt.plusSeconds(60)

        val verified = issued().verify(token, at)

        assertEquals(EmailVerificationStatus.VERIFIED, verified.statusAt(at))
        assertEquals(at, verified.verifiedAt)
    }

    /** 사용자가 링크를 두 번 누르거나 브라우저가 재전송하는 일은 흔하다. 실패로 보일 이유가 없다. */
    @Test
    fun `이미 인증된 건을 다시 인증해도 성공한다`() {
        val at = issuedAt.plusSeconds(60)
        val verified = issued().verify(token, at)

        val again = verified.verify(token, at.plusSeconds(1))

        assertSame(verified, again)
        assertEquals(at, again.verifiedAt)
    }

    @Test
    fun `토큰이 다르면 인증하지 않는다`() {
        assertFailsWith<InvalidVerificationTokenException> {
            issued().verify(VerificationToken.of("other-token"), issuedAt)
        }
    }

    @Test
    fun `기한이 지난 뒤에는 인증할 수 없다`() {
        val afterExpiry = issuedAt + EmailVerification.TIME_TO_LIVE

        assertFailsWith<VerificationExpiredException> { issued().verify(token, afterExpiry) }
    }

    /**
     * 링크 기한과 소비 기한은 별개다. 하나로 합치면 마감 직전에 인증한 사용자에게
     * 가입할 시간이 거의 남지 않는다.
     */
    @Test
    fun `링크 기한이 지나도 이미 인증했다면 가입할 수 있다`() {
        val verifiedAt = issuedAt.plusSeconds(60)
        val verified = issued().verify(token, verifiedAt)
        val afterLinkExpiry = issuedAt + EmailVerification.TIME_TO_LIVE

        assertEquals(EmailVerificationStatus.VERIFIED, verified.statusAt(afterLinkExpiry))
    }

    @Test
    fun `인증 후 소비 기한이 지나면 만료된다`() {
        val verifiedAt = issuedAt.plusSeconds(60)
        val verified = issued().verify(token, verifiedAt)
        val afterConsumeExpiry = verifiedAt + EmailVerification.CONSUME_TIME_TO_LIVE

        assertEquals(EmailVerificationStatus.EXPIRED, verified.statusAt(afterConsumeExpiry))
        assertFailsWith<VerificationExpiredException> { verified.consume(afterConsumeExpiry) }
    }

    @Test
    fun `인증하지 않은 건은 소비할 수 없다`() {
        assertFailsWith<VerificationNotCompletedException> { issued().consume(issuedAt) }
    }

    @Test
    fun `소비하면 사용 완료 상태가 된다`() {
        val verifiedAt = issuedAt.plusSeconds(60)
        val consumedAt = verifiedAt.plusSeconds(30)

        val consumed = issued().verify(token, verifiedAt).consume(consumedAt)

        assertEquals(EmailVerificationStatus.CONSUMED, consumed.statusAt(consumedAt))
        assertNotNull(consumed.consumedAt)
    }

    /** 이 규칙이 없으면 인증 한 번으로 계정을 여러 개 만들 수 있다. */
    @Test
    fun `한 번 소비한 인증은 다시 쓸 수 없다`() {
        val verifiedAt = issuedAt.plusSeconds(60)
        val consumed = issued().verify(token, verifiedAt).consume(verifiedAt.plusSeconds(30))

        assertFailsWith<VerificationAlreadyUsedException> { consumed.consume(verifiedAt.plusSeconds(40)) }
    }

    @Test
    fun `소비된 뒤에는 인증도 거부한다`() {
        val verifiedAt = issuedAt.plusSeconds(60)
        val consumed = issued().verify(token, verifiedAt).consume(verifiedAt.plusSeconds(30))

        assertFailsWith<VerificationAlreadyUsedException> { consumed.verify(token, verifiedAt.plusSeconds(40)) }
    }

    /** 토큰이 로그로 새면 인증 열쇠가 새는 것과 같다. */
    @Test
    fun `문자열 표현에 토큰이 담기지 않는다`() {
        assertEquals(false, issued().toString().contains("token-1"))
    }
}
