package com.example.shopapi.core.domain.port

import java.time.Instant

/**
 * 현재 시각.
 *
 * 도메인 안에서 `Instant.now()` 를 직접 부르면 "30분 뒤 만료" 를 테스트할 방법이 없다.
 * 시간을 주입 가능한 의존성으로 만들어 만료 로직을 결정적으로 검증한다.
 */
fun interface TimeProvider {
    fun now(): Instant
}
