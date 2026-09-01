package com.example.shopapi.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

/**
 * 인증 요청의 남용 방지(ADR 0009).
 *
 * **테스트마다 다른 원격 주소를 쓴다.** 인메모리 카운터는 컨텍스트와 수명을 같이 하므로,
 * 같은 주소를 쓰면 앞선 테스트가 소비한 한도가 다음 테스트로 넘어가 실행 순서에 따라
 * 결과가 달라진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "security.rate-limit.verification-per-ip.limit=6",
        "security.rate-limit.verification-per-ip.window=1h",
        "security.rate-limit.verification-per-email.limit=2",
        "security.rate-limit.verification-per-email.window=1h",
    ],
)
class VerificationRateLimitTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    /** 없으면 아무나 남의 주소로 메일을 무한정 보낼 수 있고 그 비용은 우리가 낸다. */
    @Test
    fun `같은 주소로 반복 요청하면 막는다`() {
        val ip = "10.0.0.1"
        request("bomb@example.com", ip).andExpect(status().isCreated)
        request("bomb@example.com", ip).andExpect(status().isCreated)

        request("bomb@example.com", ip)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
    }

    /** 언제 다시 시도할지 알려 주지 않으면 클라이언트가 계속 두드린다. */
    @Test
    fun `막을 때 다시 시도할 시각을 알려준다`() {
        val ip = "10.0.0.2"
        repeat(2) { request("retry@example.com", ip) }

        request("retry@example.com", ip)
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber)
    }

    /**
     * 열거는 서로 다른 주소를 시도하는 것이라 이메일 한도로는 막히지 않는다.
     * IP 한도가 그것을 막는다.
     */
    @Test
    fun `서로 다른 주소를 계속 시도해도 IP 한도에 걸린다`() {
        val ip = "10.0.0.3"
        // 이메일 한도(2)에 걸리지 않도록 매번 다른 주소를 쓴다.
        repeat(6) { request("enum$it@example.com", ip).andExpect(status().isCreated) }

        request("enum99@example.com", ip)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
    }

    private fun request(
        email: String,
        clientIp: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}""")
                .with {
                    it.remoteAddr = clientIp
                    it
                },
        )
}
