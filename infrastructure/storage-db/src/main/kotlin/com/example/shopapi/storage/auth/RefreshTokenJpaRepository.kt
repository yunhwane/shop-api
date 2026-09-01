package com.example.shopapi.storage.auth

import org.springframework.data.jpa.repository.JpaRepository

internal interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    fun deleteByTokenHash(tokenHash: String)

    fun deleteAllByUserId(userId: Long)
}
