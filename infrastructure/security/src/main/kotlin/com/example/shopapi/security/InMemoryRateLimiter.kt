package com.example.shopapi.security

import com.example.shopapi.core.domain.port.RateLimitPolicy
import com.example.shopapi.core.domain.port.RateLimitResult
import com.example.shopapi.core.domain.port.RateLimiter
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

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
    private val lastPurgedAt = AtomicReference(Instant.EPOCH)

    override fun tryConsume(
        key: String,
        policy: RateLimitPolicy,
        now: Instant,
    ): RateLimitResult {
        purgeIfDue(now)

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

    override fun refund(
        key: String,
        now: Instant,
    ) {
        windows.computeIfPresent(key) { _, existing ->
            // 창이 이미 바뀌었다면 되돌릴 대상이 없다. 새 창의 카운트를 깎으면 안 된다.
            if (now >= existing.expiresAt) {
                existing
            } else {
                Window(expiresAt = existing.expiresAt, count = (existing.count - 1).coerceAtLeast(0))
            }
        }
    }

    /**
     * 끝난 창을 치운다.
     *
     * 크기만 보고 훑으면 **공격 중에 가장 비싸진다.** 창이 한 시간짜리라 항목이 오래
     * 살아 있어서, 서로 다른 IP 가 임계치를 넘기면 매 요청이 지도 전체를 훑으면서
     * 아무것도 지우지 못한다. 열거를 막으려는 장치가 열거에 부하로 협조하는 셈이다.
     * 그래서 빈도를 시간으로 묶는다.
     */
    private fun purgeIfDue(now: Instant) {
        val last = lastPurgedAt.get()
        if (now < last + PURGE_INTERVAL) return
        if (!lastPurgedAt.compareAndSet(last, now)) return

        windows.entries.removeIf { now >= it.value.expiresAt }
    }

    private class Window(
        val expiresAt: Instant,
        val count: Int,
    )

    private companion object {
        val PURGE_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
