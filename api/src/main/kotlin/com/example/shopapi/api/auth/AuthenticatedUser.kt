package com.example.shopapi.api.auth

/**
 * 인증된 요청의 주체. 액세스 토큰에서 꺼낸 식별자만 들고 있다.
 *
 * 회원 정보를 담지 않는다. 토큰은 발급 시점의 사실이라 그 안의 값이 지금도 맞다는 보장이
 * 없다. 필요한 정보는 그때그때 저장소에서 읽는다.
 */
data class AuthenticatedUser(
    val id: Long,
)
