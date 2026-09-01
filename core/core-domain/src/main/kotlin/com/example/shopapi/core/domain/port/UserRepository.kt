package com.example.shopapi.core.domain.port

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.user.User
import com.example.shopapi.core.domain.user.UserId

/**
 * 회원 저장소.
 *
 * [existsByUserId] / [existsByEmail] 은 중복을 **막는** 수단이 아니다. 두 요청이
 * 동시에 들어오면 둘 다 false 를 보고 통과한다. 실제 방어선은 DB 유니크 제약이고,
 * 구현체는 제약 위반을 DuplicateUserIdException / DuplicateEmailException 으로
 * 번역할 책임이 있다(ADR 0005).
 */
interface UserRepository {
    fun save(user: User): User

    fun findById(id: Long): User?

    fun findByUserId(userId: UserId): User?

    fun existsByUserId(userId: UserId): Boolean

    fun existsByEmail(email: Email): Boolean
}
