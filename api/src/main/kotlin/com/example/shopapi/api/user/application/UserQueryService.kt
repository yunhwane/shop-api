package com.example.shopapi.api.user.application

import com.example.shopapi.core.domain.auth.UnauthenticatedException
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
     *
     * `INVALID_CREDENTIALS` 를 쓰지 않는다. 이 요청은 아이디와 비밀번호를 보내지 않았으므로
     * "아이디 또는 비밀번호가 올바르지 않습니다"가 상황과 어긋나고, 클라이언트가 로그인 실패와
     * 세션 만료를 구분하지 못한다.
     */
    @Transactional(readOnly = true)
    fun findMe(id: Long): User = users.findById(id) ?: throw UnauthenticatedException()
}
