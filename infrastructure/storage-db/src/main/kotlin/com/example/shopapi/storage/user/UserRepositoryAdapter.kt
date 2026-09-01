package com.example.shopapi.storage.user

import com.example.shopapi.core.domain.common.Email
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.DuplicateEmailException
import com.example.shopapi.core.domain.user.DuplicateUserIdException
import com.example.shopapi.core.domain.user.User
import com.example.shopapi.core.domain.user.UserId
import com.example.shopapi.storage.support.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
internal class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {
    /**
     * `saveAndFlush` 를 쓴다. 일반 `save` 는 INSERT 를 커밋 시점까지 미룰 수 있어,
     * 제약 위반이 이 try 블록 **바깥**에서 터진다. 그러면 번역하지 못한 JPA 예외가
     * 그대로 api 까지 올라간다.
     */
    override fun save(user: User): User =
        try {
            jpaRepository.saveAndFlush(UserJpaEntity.from(user)).toDomain()
        } catch (e: DataIntegrityViolationException) {
            when {
                e.violates(UserJpaEntity.UK_USER_ID) -> throw DuplicateUserIdException(user.userId)
                e.violates(UserJpaEntity.UK_EMAIL) -> throw DuplicateEmailException()
                else -> throw e
            }
        }

    override fun findById(id: Long): User? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByUserId(userId: UserId): User? = jpaRepository.findByUserId(userId.value)?.toDomain()

    override fun existsByUserId(userId: UserId): Boolean = jpaRepository.existsByUserId(userId.value)

    override fun existsByEmail(email: Email): Boolean = jpaRepository.existsByEmail(email.value)
}
