package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.enums.UserStatus
import java.time.Instant

/**
 * 회원.
 *
 * 이메일 인증을 가입보다 먼저 수행하므로(ADR 0001) 이 객체가 존재한다는 것은
 * 이메일 소유가 이미 확인됐다는 뜻이다. 미인증 상태는 존재하지 않는다.
 */
class User(
    val id: Long?,
    val userId: UserId,
    val email: Email,
    val password: EncodedPassword,
    val status: UserStatus,
    val createdAt: Instant,
) {
    override fun toString(): String = "User(id=$id, userId=$userId, status=$status)"

    companion object {
        /** 아직 저장되지 않은 신규 회원. [id] 는 저장 시점에 채워진다 */
        fun register(
            userId: UserId,
            email: Email,
            password: EncodedPassword,
            now: Instant,
        ): User =
            User(
                id = null,
                userId = userId,
                email = email,
                password = password,
                status = UserStatus.ACTIVE,
                createdAt = now,
            )
    }
}
