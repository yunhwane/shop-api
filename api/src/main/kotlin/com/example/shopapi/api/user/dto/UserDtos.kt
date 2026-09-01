package com.example.shopapi.api.user.dto

import com.example.shopapi.core.domain.user.User
import com.example.shopapi.core.enums.UserStatus

/**
 * 회원가입 요청.
 *
 * **`email` 이 없다.** 이메일은 서버가 [verificationId] 로 조회한 인증 레코드에서 꺼낸다.
 * 바디로 받으면 A 를 인증하고 B 로 가입하는 경로가 열린다(ADR 0002).
 */
data class SignUpRequest(
    val verificationId: String,
    val userId: String,
    val password: String,
)

data class SignUpResponse(
    val id: Long,
    val userId: String,
    val email: String,
) {
    companion object {
        fun from(user: User): SignUpResponse =
            SignUpResponse(
                id = requireNotNull(user.id) { "저장된 회원이어야 한다" },
                userId = user.userId.value,
                email = user.email.value,
            )
    }
}

data class MeResponse(
    val id: Long,
    val userId: String,
    val email: String,
    val status: UserStatus,
) {
    companion object {
        fun from(user: User): MeResponse =
            MeResponse(
                id = requireNotNull(user.id) { "저장된 회원이어야 한다" },
                userId = user.userId.value,
                email = user.email.value,
                status = user.status,
            )
    }
}
