package com.example.shopapi.core.enums

/**
 * 회원의 운영 상태.
 *
 * 이메일 인증을 가입보다 먼저 수행하므로(ADR 0001) `PENDING` 은 존재하지 않는다.
 * User 레코드가 존재한다면 이미 인증된 계정이다.
 */
enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN,
}
