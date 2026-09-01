package com.example.shopapi.storage.user

import org.springframework.data.jpa.repository.JpaRepository

internal interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByUserId(userId: String): UserJpaEntity?

    fun existsByUserId(userId: String): Boolean

    fun existsByEmail(email: String): Boolean
}
