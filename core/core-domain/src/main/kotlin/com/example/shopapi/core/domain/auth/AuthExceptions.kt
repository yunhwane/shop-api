package com.example.shopapi.core.domain.auth

import com.example.shopapi.core.domain.common.DomainException
import com.example.shopapi.core.enums.ErrorCode

/**
 * 아이디가 없거나 비밀번호가 틀렸다.
 *
 * 둘을 구분하지 않는다. 구분하면 아이디 목록이 새어 나간다(ADR 0008).
 */
class InvalidCredentialsException : DomainException(ErrorCode.INVALID_CREDENTIALS)

class AccountSuspendedException : DomainException(ErrorCode.ACCOUNT_SUSPENDED)

class AccountWithdrawnException : DomainException(ErrorCode.ACCOUNT_WITHDRAWN)

/** 리프레시 토큰을 찾지 못했거나 기한이 지났다. */
class InvalidRefreshTokenException : DomainException(ErrorCode.INVALID_REFRESH_TOKEN)

class RefreshTokenExpiredException : DomainException(ErrorCode.INVALID_REFRESH_TOKEN)

/**
 * 자격은 제시됐으나 그것으로 요청을 수행할 수 없다.
 *
 * [InvalidCredentialsException] 과 구분한다. 그쪽은 아이디와 비밀번호를 보낸 요청에 대한
 * 답이라, 자격을 보내지도 않은 요청에 쓰면 메시지가 상황과 어긋난다.
 */
class UnauthenticatedException : DomainException(ErrorCode.UNAUTHENTICATED)

/**
 * 이미 소비된 리프레시 토큰이 다시 왔다.
 *
 * [userId] 를 들고 다니는 이유는 호출자가 이 사용자의 토큰을 전부 지워야 하기 때문이다.
 * 회전만으로는 탈취를 막지 못한다 - 탈취범이 먼저 재발급을 받아 가면 정상 사용자가
 * 소비된 토큰을 들고 오게 되고, 그 사실 자체가 유출의 신호다(ADR 0008).
 */
class RefreshTokenReusedException(
    val userId: Long,
) : DomainException(ErrorCode.REFRESH_TOKEN_REUSED)
