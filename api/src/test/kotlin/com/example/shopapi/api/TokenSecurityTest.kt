package com.example.shopapi.api

import com.example.shopapi.core.domain.auth.RefreshToken
import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.port.RefreshTokenRepository
import com.example.shopapi.core.domain.verification.VerificationId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 토큰 경로가 계정 상태와 경합에 대해 무엇을 보장하는지 고정한다.
 *
 * 계정 정지는 API 로 만들 수 없어 JDBC 로 직접 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        "security.bcrypt.strength=4",
        // 이 테스트들이 검증하는 것은 호출 제한이 아니다. RateLimitTest 가 따로 본다.
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.rate-limit.login-failure-per-ip.limit=1000",
    ],
)
class TokenSecurityTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
    @param:Autowired private val refreshTokens: RefreshTokenRepository,
    @param:Autowired private val dataSource: DataSource,
) {
    /**
     * 로그인만 상태를 검사하면, 정지된 뒤에도 재발급으로 접근이 유지된다. 회전이 만료를
     * 매번 갱신하므로 사실상 끊기지 않는 세션이 되고 계정 정지가 아무 의미가 없어진다.
     */
    @Test
    fun `정지된 계정은 재발급으로 접근을 이어갈 수 없다`() {
        signUp("suspend01", "suspend01@example.com")
        val refreshToken = extract(login("suspend01").andReturn().response.contentAsString, "refreshToken")

        execute("UPDATE users SET status = 'SUSPENDED' WHERE user_id = 'suspend01'")

        reissue(refreshToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
    }

    @Test
    fun `정지된 계정은 로그인도 막힌다`() {
        signUp("suspend02", "suspend02@example.com")
        execute("UPDATE users SET status = 'SUSPENDED' WHERE user_id = 'suspend02'")

        login("suspend02")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
    }

    /**
     * 소비 처리는 DB 가 한 번만 성공시켜야 한다. 조회하고 바꿔 쓰는 방식이면 같은 토큰으로
     * 동시에 들어온 요청이 둘 다 통과해 재사용 탐지가 그대로 우회된다.
     */
    @Test
    fun `같은 토큰의 소비는 한 번만 성공한다`() {
        val now = Instant.parse("2026-09-01T00:00:00Z")
        val saved =
            refreshTokens.save(
                RefreshToken.issue(
                    userId = 9999L,
                    tokenHash = "race-probe-hash",
                    expiresAt = now.plusSeconds(3600),
                    now = now,
                ),
            )
        val id = assertNotNull(saved.id)

        assertTrue(refreshTokens.markUsedIfUnused(id, now), "첫 소비는 성공해야 한다")
        assertFalse(refreshTokens.markUsedIfUnused(id, now), "이미 소비된 토큰은 실패해야 한다")
    }

    /**
     * 자격을 보내지도 않은 요청에 "아이디 또는 비밀번호가 올바르지 않습니다"가 나가면
     * 클라이언트가 로그인 실패와 세션 만료를 구분하지 못한다.
     */
    @Test
    fun `회원이 사라진 토큰으로 조회하면 인증 오류를 준다`() {
        signUp("ghost0001", "ghost0001@example.com")
        val accessToken = extract(login("ghost0001").andReturn().response.contentAsString, "accessToken")

        execute("DELETE FROM users WHERE user_id = 'ghost0001'")

        mockMvc
            .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }
    }

    private fun login(userId: String): ResultActions =
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$userId","password":"$PASSWORD"}"""),
        )

    private fun reissue(refreshToken: String): ResultActions =
        mockMvc.perform(
            post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken"}"""),
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
        val verificationId = extract(issued, "verificationId")
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

    private fun extract(
        body: String,
        field: String,
    ): String = Regex("\"$field\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    private companion object {
        const val PASSWORD = "password1"
    }
}
