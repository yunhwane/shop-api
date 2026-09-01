package com.example.shopapi.security

import com.example.shopapi.core.domain.port.RateLimitPolicy
import com.example.shopapi.core.domain.port.RateLimitResult
import com.example.shopapi.core.domain.port.RateLimiter
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 프로세스 메모리에 두는 고정 창 카운터(ADR 0009).
 *
 * 한계를 분명히 해 둔다. **인스턴스마다 카운터가 따로이고 재시작하면 초기화된다.**
 * 서버를 늘리는 순간 실질 한도가 대수만큼 곱해지므로 공유 저장소로 옮겨야 한다.
 * 고정 창이라 창이 바뀌는 경계에서 한도의 두 배까지 지나갈 수 있다.
 */
@Component
internal class InMemoryRateLimiter : RateLimiter {
    private val windows = ConcurrentHashMap<String, Window>()

    override fun check(
        key: String,
        policy: RateLimitPolicy,
        now: Instant,
    ): RateLimitResult {
        val window = windows[key]
        if (window == null || now >= window.expiresAt) return RateLimitResult.Allowed

        // 부등호가 tryConsume 과 다르다. 이쪽은 "예산이 남았는가"를 묻고,
        // 그쪽은 "방금 소비한 이 호출이 한도 안이었는가"를 묻는다.
        // 같은 비교를 쓰면 한도가 3일 때 네 번째 시도까지 통과한다.
        return if (window.count < policy.limit) {
            RateLimitResult.Allowed
        } else {
            RateLimitResult.Rejected(Duration.between(now, window.expiresAt))
        }
    }

    override fun tryConsume(
        key: String,
        policy: RateLimitPolicy,
        now: Instant,
    ): RateLimitResult {
        purgeIfCrowded(now)

        // compute 는 키 단위로 원자적이다. 읽고 더하는 식으로 쓰면 동시 요청이 같은 값을
        // 읽어 한도를 넘겨 통과한다.
        val window =
            windows.compute(key) { _, existing ->
                if (existing == null || now >= existing.expiresAt) {
                    Window(expiresAt = now + policy.window, count = 1)
                } else {
                    Window(expiresAt = existing.expiresAt, count = existing.count + 1)
                }
            }!!

        return if (window.count <= policy.limit) {
            RateLimitResult.Allowed
        } else {
            RateLimitResult.Rejected(Duration.between(now, window.expiresAt))
        }
    }

    /**
     * 키가 무한히 쌓이는 것을 막는다. 창이 끝난 항목은 더 이상 아무 의미가 없다.
     *
     * 주기적인 청소 대신 크기가 커졌을 때만 훑는다. 이 클래스가 스레드나 스케줄러를
     * 들고 있지 않게 하려는 것이다.
     */
    private fun purgeIfCrowded(now: Instant) {
        if (windows.size < PURGE_THRESHOLD) return
        windows.entries.removeIf { now >= it.value.expiresAt }
    }

    private class Window(
        val expiresAt: Instant,
        val count: Int,
    )

    private companion object {
        const val PURGE_THRESHOLD = 10_000
    }
}
