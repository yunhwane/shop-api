package com.example.shopapi.storage.auth

import com.example.shopapi.core.domain.auth.RefreshToken
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
internal class RefreshTokenRepositoryAdapter(
    private val jpaRepository: RefreshTokenJpaRepository,
) : RefreshTokenRepository {
    override fun save(token: RefreshToken): RefreshToken =
        jpaRepository.saveAndFlush(RefreshTokenJpaEntity.from(token)).toDomain()

    override fun findByTokenHash(tokenHash: String): RefreshToken? =
        jpaRepository.findByTokenHash(tokenHash)?.toDomain()

    // 쓰기 쿼리라 트랜잭션이 필요하다. 바깥 트랜잭션이 있으면 거기에 참여하고,
    // 없으면 스스로 연다. 호출 맥락에 따라 동작이 달라지지 않게 한다.
    @Transactional
    override fun markUsedIfUnused(
        id: Long,
        usedAt: Instant,
    ): Boolean = jpaRepository.markUsedIfUnused(id, usedAt) == 1

    override fun deleteByTokenHash(tokenHash: String) {
        jpaRepository.deleteByTokenHash(tokenHash)
    }

    @Transactional
    override fun deleteExpiredBefore(now: Instant): Int = jpaRepository.deleteExpiredBefore(now)

    override fun deleteAllByUserId(userId: Long) {
        jpaRepository.deleteAllByUserId(userId)
    }
}
