package com.example.shopapi.storage.auth

import com.example.shopapi.core.domain.auth.RefreshToken
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 리프레시 토큰의 영속성 모델.
 *
 * `user_id` 에 인덱스를 두는 이유는 재사용이 탐지될 때 해당 사용자의 토큰을 전부
 * 지우기 때문이다(ADR 0008). 회전 때문에 한 사용자에 여러 행이 쌓인다.
 */
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = [
        UniqueConstraint(name = RefreshTokenJpaEntity.UK_TOKEN_HASH, columnNames = ["token_hash"]),
    ],
    indexes = [Index(name = "ix_refresh_tokens_user_id", columnList = "user_id")],
)
class RefreshTokenJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "token_hash", nullable = false, length = 64)
    var tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "used_at")
    var usedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toDomain(): RefreshToken =
        RefreshToken(
            id = id,
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            usedAt = usedAt,
            createdAt = createdAt,
        )

    companion object {
        const val UK_TOKEN_HASH = "uk_refresh_tokens_token_hash"

        fun from(token: RefreshToken): RefreshTokenJpaEntity =
            RefreshTokenJpaEntity(
                id = token.id,
                userId = token.userId,
                tokenHash = token.tokenHash,
                expiresAt = token.expiresAt,
                usedAt = token.usedAt,
                createdAt = token.createdAt,
            )
    }
}
