package com.example.shopapi.storage.user

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.user.EncodedPassword
import com.example.shopapi.core.domain.user.User
import com.example.shopapi.core.domain.user.UserId
import com.example.shopapi.core.enums.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 회원의 영속성 모델. 도메인 모델([User])과 별개 클래스다.
 *
 * 제약에 이름을 붙이는 이유는 위반 시 어느 컬럼이 충돌했는지 구분하기 위해서다.
 * 이름이 없으면 DB 가 임의로 지어낸 이름이 메시지에 실려 판별할 수 없다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = UserJpaEntity.UK_USER_ID, columnNames = ["user_id"]),
        UniqueConstraint(name = UserJpaEntity.UK_EMAIL, columnNames = ["email"]),
    ],
)
class UserJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "user_id", nullable = false, length = 20)
    var userId: String,
    @Column(name = "email", nullable = false, length = 254)
    var email: String,
    @Column(name = "password", nullable = false, length = 100)
    var password: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toDomain(): User =
        User(
            id = id,
            userId = UserId.of(userId),
            email = Email.of(email),
            password = EncodedPassword.of(password),
            status = status,
            createdAt = createdAt,
        )

    companion object {
        const val UK_USER_ID = "uk_users_user_id"
        const val UK_EMAIL = "uk_users_email"

        fun from(user: User): UserJpaEntity =
            UserJpaEntity(
                id = user.id,
                userId = user.userId.value,
                email = user.email.value,
                password = user.password.value,
                status = user.status,
                createdAt = user.createdAt,
            )
    }
}
