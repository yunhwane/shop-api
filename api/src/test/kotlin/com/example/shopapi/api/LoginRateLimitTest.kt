package com.example.shopapi.api

import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.VerificationId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 로그인의 남용 방지(ADR 0009). 실패만 세고 IP 단위로만 센다.
 *
 * **테스트마다 다른 원격 주소를 쓴다.** 인메모리 카운터가 컨텍스트와 수명을 같이 해서,
 * 같은 주소를 쓰면 앞선 테스트가 소비한 한도가 다음 테스트로 넘어간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "security.bcrypt.strength=4",
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.rate-limit.login-failure-per-ip.limit=3",
        "security.rate-limit.login-failure-per-ip.window=5m",
    ],
)
class LoginRateLimitTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
) {
    /**
     * 성공까지 세면 여러 사람이 한 IP 를 쓰는 환경에서 아무도 공격하지 않았는데 한도가 찬다.
     * 실패 3회가 한도인 설정에서, 성공을 아무리 반복해도 한도가 줄지 않아야 한다.
     */
    @Test
    fun `성공한 로그인은 한도를 깎지 않는다`() {
        signUp("ratelimit1", "ratelimit1@example.com")

        val ip = "10.1.0.1"
        repeat(10) { login("ratelimit1", PASSWORD, ip).andExpect(status().isOk) }

        // 실패 예산이 그대로 남아 있어야 한다.
        repeat(3) { login("ratelimit1", "wrongPass1", ip).andExpect(status().isUnauthorized) }
    }

    @Test
    fun `실패가 한도를 넘으면 막는다`() {
        val ip = "10.1.0.2"
        repeat(3) {
            login("nosuchuser$it", "wrongPass1", ip).andExpect(status().isUnauthorized)
        }

        login("nosuchuser99", "wrongPass1", ip)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
    }

    private fun login(
        userId: String,
        password: String,
        clientIp: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$userId","password":"$password"}""")
                .with {
                    it.remoteAddr = clientIp
                    it
                },
        )

    private fun signUp(
        userId: String,
        email: String,
    ) {
        val issued =
            mockMvc
                .perform(
                    post("/api/v1/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val verificationId = Regex("\"verificationId\":\"([^\"]+)\"").find(issued)!!.groupValues[1]
        val verification = verifications.findByVerificationId(VerificationId.of(verificationId))
        assertNotNull(verification)

        mockMvc.perform(
            post("/api/v1/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${verification.token.value}"}"""),
        )
        mockMvc
            .perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"verificationId":"$verificationId","userId":"$userId","password":"$PASSWORD"}"""),
            ).andExpect(status().isCreated)
    }

    private companion object {
        const val PASSWORD = "password1"
    }
}
