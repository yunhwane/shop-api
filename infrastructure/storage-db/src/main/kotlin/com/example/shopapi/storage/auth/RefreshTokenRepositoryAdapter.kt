package com.example.shopapi.storage.auth

import com.example.shopapi.core.domain.auth.RefreshToken
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import org.springframework.stereotype.Repository

@Repository
internal class RefreshTokenRepositoryAdapter(
    private val jpaRepository: RefreshTokenJpaRepository,
) : RefreshTokenRepository {
    override fun save(token: RefreshToken): RefreshToken =
        jpaRepository.saveAndFlush(RefreshTokenJpaEntity.from(token)).toDomain()

    override fun findByTokenHash(tokenHash: String): RefreshToken? =
        jpaRepository.findByTokenHash(tokenHash)?.toDomain()

    override fun deleteByTokenHash(tokenHash: String) {
        jpaRepository.deleteByTokenHash(tokenHash)
    }

    override fun deleteAllByUserId(userId: Long) {
        jpaRepository.deleteAllByUserId(userId)
    }
}
