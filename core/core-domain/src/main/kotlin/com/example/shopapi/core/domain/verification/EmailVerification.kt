package com.example.shopapi.core.domain.verification

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.enums.EmailVerificationStatus
import java.time.Duration
import java.time.Instant

/**
 * 이메일 소유 확인 절차 하나. 가입보다 먼저 완료되어야 한다(ADR 0001).
 *
 * 상태를 컬럼으로 들지 않고 세 타임스탬프에서 파생한다([statusAt]). 저장된 상태와
 * 타임스탬프가 어긋날 여지를 없앤다.
 *
 * 기한이 둘인 점에 주의한다.
 * - [expiresAt] : 메일 링크의 기한. 누르지 않은 채 지나면 만료된다.
 * - [verifiedAt] + [CONSUME_TIME_TO_LIVE] : 인증 후 가입까지의 기한.
 *
 * 하나로 합치면 마감 1분 전에 인증한 사용자에게 가입할 시간이 1분밖에 남지 않는다.
 * 그렇다고 인증 후 무기한으로 두면 소비되지 않은 자격이 계속 살아 있게 된다.
 */
class EmailVerification(
    val id: Long?,
    val verificationId: VerificationId,
    val token: VerificationToken,
    val email: Email,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val consumedAt: Instant?,
    val createdAt: Instant,
) {
    /**
     * 지금 이 인증이 어떤 상태인가.
     *
     * 소비 여부를 가장 먼저 본다. 소비된 인증은 기한과 무관하게 끝난 것이다.
     */
    fun statusAt(now: Instant): EmailVerificationStatus =
        when {
            consumedAt != null -> {
                EmailVerificationStatus.CONSUMED
            }

            verifiedAt != null -> {
                if (now < verifiedAt + CONSUME_TIME_TO_LIVE) {
                    EmailVerificationStatus.VERIFIED
                } else {
                    EmailVerificationStatus.EXPIRED
                }
            }

            now < expiresAt -> {
                EmailVerificationStatus.PENDING
            }

            else -> {
                EmailVerificationStatus.EXPIRED
            }
        }

    /**
     * 메일 링크 클릭. 인증을 완료한다.
     *
     * 토큰을 받지 않는다. 이 객체는 토큰으로 조회해서 얻은 것이므로, 호출자가 토큰을
     * 알고 있다는 사실이 조회 성공으로 이미 증명됐다. 여기서 한 번 더 비교해도 항상
     * 참이라 검사처럼 보이지만 아무것도 걸러내지 못한다.
     *
     * 이미 인증된 건에 대해서는 멱등하게 자신을 돌려준다. 사용자가 링크를 두 번 누르거나
     * 브라우저가 요청을 재전송하는 일이 흔하고, 그것을 실패로 보여 줄 이유가 없다.
     */
    fun verify(now: Instant): EmailVerification =
        when (statusAt(now)) {
            EmailVerificationStatus.CONSUMED -> throw VerificationAlreadyUsedException()
            EmailVerificationStatus.EXPIRED -> throw VerificationExpiredException()
            EmailVerificationStatus.VERIFIED -> this
            EmailVerificationStatus.PENDING -> copyWith(verifiedAt = now, consumedAt = null)
        }

    /**
     * 가입에 사용한다. 한 번만 성공한다.
     *
     * 소비 처리하지 않으면 인증 하나로 계정을 여러 개 만들 수 있다.
     */
    fun consume(now: Instant): EmailVerification =
        when (statusAt(now)) {
            EmailVerificationStatus.CONSUMED -> throw VerificationAlreadyUsedException()
            EmailVerificationStatus.EXPIRED -> throw VerificationExpiredException()
            EmailVerificationStatus.PENDING -> throw VerificationNotCompletedException()
            EmailVerificationStatus.VERIFIED -> copyWith(verifiedAt = verifiedAt, consumedAt = now)
        }

    private fun copyWith(
        verifiedAt: Instant?,
        consumedAt: Instant?,
    ): EmailVerification =
        EmailVerification(
            id = id,
            verificationId = verificationId,
            token = token,
            email = email,
            expiresAt = expiresAt,
            verifiedAt = verifiedAt,
            consumedAt = consumedAt,
            createdAt = createdAt,
        )

    /** token 은 담지 않는다. 이 객체가 로그에 찍혀도 인증 열쇠가 새지 않아야 한다 */
    override fun toString(): String = "EmailVerification(id=$id, verificationId=$verificationId, email=$email)"

    companion object {
        val TIME_TO_LIVE: Duration = Duration.ofMinutes(30)

        val CONSUME_TIME_TO_LIVE: Duration = Duration.ofMinutes(30)

        fun issue(
            verificationId: VerificationId,
            token: VerificationToken,
            email: Email,
            now: Instant,
        ): EmailVerification =
            EmailVerification(
                id = null,
                verificationId = verificationId,
                token = token,
                email = email,
                expiresAt = now + TIME_TO_LIVE,
                verifiedAt = null,
                consumedAt = null,
                createdAt = now,
            )
    }
}
