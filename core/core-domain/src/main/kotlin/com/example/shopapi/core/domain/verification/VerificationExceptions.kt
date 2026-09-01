package com.example.shopapi.core.domain.verification

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

class VerificationNotFoundException : DomainException(ErrorCode.VERIFICATION_NOT_FOUND)

/** 기한이 지났다. 처음부터 다시 요청해야 한다. */
class VerificationExpiredException : DomainException(ErrorCode.VERIFICATION_EXPIRED)

/** 아직 메일 링크를 누르지 않았다. */
class VerificationNotCompletedException : DomainException(ErrorCode.VERIFICATION_NOT_COMPLETED)

/** 이미 가입에 사용된 인증이다. 근거는 [EmailVerification.consume] 에 적혀 있다. */
class VerificationAlreadyUsedException : DomainException(ErrorCode.VERIFICATION_ALREADY_USED)

class InvalidVerificationTokenException : DomainException(ErrorCode.INVALID_VERIFICATION_TOKEN)
