package com.example.shopapi.api

import com.example.shopapi.core.domain.port.EmailVerificationRepository
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
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * 로그인과 토큰 회전을 HTTP 로 훑는다.
 *
 * bcrypt 강도를 낮춰 잡는다. 기본값 10 은 한 번에 수십 ms 라 로그인이 많은 이 테스트가
 * 눈에 띄게 느려진다. 검증하려는 것은 해싱 비용이 아니라 흐름이다.
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
class LoginFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
) {
    @Test
    fun `가입한 계정으로 로그인하면 토큰 쌍을 받는다`() {
        signUp("login01", "login01@example.com")

        login("login01", PASSWORD)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.refreshToken").exists())
            .andExpect(jsonPath("$.data.accessTokenExpiresAt").exists())
            .andExpect(jsonPath("$.data.refreshTokenExpiresAt").exists())
    }

    @Test
    fun `비밀번호가 틀리면 거부한다`() {
        signUp("login02", "login02@example.com")

        login("login02", "wrongPass1")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    /** 없는 아이디와 틀린 비밀번호가 같은 응답이어야 아이디 목록이 새지 않는다(ADR 0008). */
    @Test
    fun `없는 아이디도 틀린 비밀번호와 같은 응답을 준다`() {
        login("nosuchuser", PASSWORD)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    /** 형식 위반이 400 으로 갈리면 그 차이만으로 아이디 규칙을 좁혀 갈 수 있다. */
    @Test
    fun `형식을 어긴 아이디도 400 이 아니라 401 이다`() {
        login("ab", PASSWORD)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `토큰 없이 내 정보를 볼 수 없다`() {
        mockMvc
            .perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
            // 인증 실패도 나머지와 같은 ProblemDetail 이어야 한다(ADR 0006).
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.type").value("/problems/unauthenticated"))
            .andExpect(jsonPath("$.instance").value("/api/v1/users/me"))
    }

    @Test
    fun `망가진 토큰도 인증되지 않은 요청으로 다룬다`() {
        mockMvc
            .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `액세스 토큰으로 내 정보를 본다`() {
        signUp("login03", "login03@example.com")
        val tokens = loginTokens("login03")

        mockMvc
            .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.access}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value("login03"))
            .andExpect(jsonPath("$.data.email").value("login03@example.com"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
    }

    @Test
    fun `재발급하면 리프레시 토큰도 새 것으로 바뀐다`() {
        signUp("login04", "login04@example.com")
        val first = loginTokens("login04")

        val body =
            reissue(first.refresh)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        assertNotEquals(first.refresh, extract(body, "refreshToken"), "리프레시 토큰이 회전해야 한다")
    }

    /**
     * 회전만으로는 탈취를 막지 못한다. 소비된 토큰이 다시 오는 것 자체가 유출의 신호이고,
     * 그때 해당 사용자의 자격을 전부 끊는다(ADR 0008).
     */
    @Test
    fun `옛 리프레시 토큰을 다시 쓰면 모든 토큰이 끊긴다`() {
        signUp("login05", "login05@example.com")
        val first = loginTokens("login05")

        val secondRefresh =
            extract(reissue(first.refresh).andReturn().response.contentAsString, "refreshToken")

        reissue(first.refresh)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"))

        // 정상 발급됐던 새 토큰까지 함께 무효가 된다. 어느 쪽이 탈취범인지 알 수 없기 때문이다.
        reissue(secondRefresh)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
    }

    @Test
    fun `로그아웃하면 그 리프레시 토큰을 쓸 수 없다`() {
        signUp("login06", "login06@example.com")
        val tokens = loginTokens("login06")

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"${tokens.refresh}"}"""),
            ).andExpect(status().isNoContent)

        reissue(tokens.refresh)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
    }

    private fun login(
        userId: String,
        password: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$userId","password":"$password"}"""),
        )

    private fun reissue(refreshToken: String): ResultActions =
        mockMvc.perform(
            post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken"}"""),
        )

    private fun loginTokens(userId: String): Tokens {
        val body =
            login(userId, PASSWORD)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return Tokens(extract(body, "accessToken"), extract(body, "refreshToken"))
    }

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

        mockMvc
            .perform(
                post("/api/v1/email-verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"${verification.token.value}"}"""),
            ).andExpect(status().isOk)

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

    private class Tokens(
        val access: String,
        val refresh: String,
    )

    private companion object {
        const val PASSWORD = "password1"
    }
}
