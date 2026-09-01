package com.example.shopapi.api.common

/**
 * 성공 응답의 공통 봉투(ADR 0006).
 *
 * `success` 플래그를 두지 않는다. 실패는 `application/problem+json` 으로 나가므로
 * 이 봉투 안에서는 항상 참이고, 항상 참인 필드에는 정보가 없다.
 *
 * `data` 한 겹이 있으면 나중에 페이지네이션 `meta` 를 기존 필드를 건드리지 않고 붙일 수 있다.
 */
data class ApiResponse<T>(
    val data: T,
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data)
    }
}
