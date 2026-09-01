package com.example.shopapi.api.user.application

import com.example.shopapi.core.domain.auth.InvalidCredentialsException
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserQueryService(
    private val users: UserRepository,
) {
    /**
     * 토큰이 가리키는 회원을 읽는다.
     *
     * 토큰은 유효한데 회원이 없는 경우 - 탈퇴 후 삭제 같은 - 는 인증을 다시 하게 만든다.
     * 서버 오류가 아니라 그 자격이 더 이상 쓸 수 없다는 뜻이기 때문이다.
     */
    @Transactional(readOnly = true)
    fun findMe(id: Long): User = users.findById(id) ?: throw InvalidCredentialsException()
}
