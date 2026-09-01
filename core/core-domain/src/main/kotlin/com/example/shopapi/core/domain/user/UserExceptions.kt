package com.example.shopapi.core.domain.user

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

/**
 * 아이디가 이미 쓰이고 있다.
 *
 * 사전 조회에서도, 유니크 제약 위반을 어댑터가 번역해서도 던져진다.
 * 동시 요청을 실제로 막는 것은 후자다(ADR 0005).
 */
class DuplicateUserIdException(
    val userId: UserId,
) : DomainException(ErrorCode.DUPLICATE_USER_ID)

class DuplicateEmailException : DomainException(ErrorCode.DUPLICATE_EMAIL)
