package com.example.shopapi.api

import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.VerificationId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 인증 요청부터 가입까지 HTTP 로 훑는다. 응답 계약(성공 봉투 / ProblemDetail)도 여기서 검증한다.
 *
 * 메일은 `mail.provider=log` 로 실제 발송을 끈다. 인증 토큰은 메일함이 아니라
 * 저장소 포트에서 직접 꺼낸다 - 토큰은 응답에 절대 담기지 않기 때문이다(ADR 0002).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "mail.provider=log",
        // 이 테스트들이 검증하는 것은 호출 제한이 아니다. RateLimitTest 가 따로 본다.
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.rate-limit.login-failure-per-ip.limit=1000",
    ],
)
class SignUpFlowTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
) {
    private fun requestVerification(email: String): String {
        val body =
            mockMvc
                .perform(
                    post("/api/v1/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email"}"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.verificationId").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                // 토큰은 어떤 경우에도 응답에 담기지 않는다
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andReturn()
                .response.contentAsString
        return Regex("\"verificationId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun tokenOf(verificationId: String): String {
        val verification = verifications.findByVerificationId(VerificationId.of(verificationId))
        assertNotNull(verification)
        return verification.token.value
    }

    private fun confirm(token: String) {
        mockMvc
            .perform(
                post("/api/v1/email-verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$token"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("VERIFIED"))
    }

    private fun signUp(
        verificationId: String,
        userId: String,
        password: String = "password1",
    ) = mockMvc.perform(
        post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"verificationId":"$verificationId","userId":"$userId","password":"$password"}"""),
    )

    @Test
    fun `인증하고 가입하면 회원이 생성된다`() {
        val verificationId = requestVerification("flow@example.com")
        confirm(tokenOf(verificationId))

        signUp(verificationId, "flowuser1")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.userId").value("flowuser1"))
            // 이메일은 요청에 없었다. 서버가 인증 레코드에서 꺼내 온다(ADR 0002).
            .andExpect(jsonPath("$.data.email").value("flow@example.com"))
    }

    @Test
    fun `폴링으로 인증 완료를 알 수 있다`() {
        val verificationId = requestVerification("polling@example.com")

        mockMvc
            .perform(get("/api/v1/email-verifications/$verificationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PENDING"))

        confirm(tokenOf(verificationId))

        mockMvc
            .perform(get("/api/v1/email-verifications/$verificationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("VERIFIED"))
    }

    @Test
    fun `인증하지 않고는 가입할 수 없다`() {
        val verificationId = requestVerification("unverified@example.com")

        signUp(verificationId, "unverified1")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_COMPLETED"))
    }

    @Test
    fun `이미 가입된 이메일로 인증을 요청하면 409 로 알린다`() {
        val verificationId = requestVerification("duplicate@example.com")
        confirm(tokenOf(verificationId))
        signUp(verificationId, "dupuser01").andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/email-verifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"duplicate@example.com"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
    }

    @Test
    fun `실패 응답은 ProblemDetail 형식이다`() {
        val verificationId = requestVerification("problem@example.com")
        confirm(tokenOf(verificationId))
        signUp(verificationId, "taken0001").andExpect(status().isCreated)

        val second = requestVerification("problem2@example.com")
        confirm(tokenOf(second))

        signUp(second, "taken0001")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.type").value("/problems/duplicate-user-id"))
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.instance").value("/api/v1/users"))
            .andExpect(jsonPath("$.code").value("DUPLICATE_USER_ID"))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `검증 실패는 어느 필드가 틀렸는지 알려준다`() {
        val verificationId = requestVerification("invalid@example.com")
        confirm(tokenOf(verificationId))

        signUp(verificationId, userId = "ab")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.errors[0].field").value("userId"))
            .andExpect(jsonPath("$.errors[0].reason").exists())
    }

    /** 인증 하나로 계정을 여러 개 만들 수 없어야 한다. */
    @Test
    fun `같은 인증으로 두 번 가입할 수 없다`() {
        val verificationId = requestVerification("reuse@example.com")
        confirm(tokenOf(verificationId))
        signUp(verificationId, "reuseuser1").andExpect(status().isCreated)

        signUp(verificationId, "reuseuser2")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("VERIFICATION_ALREADY_USED"))
    }

    /**
     * 인증 요청은 인증이 필요 없는 엔드포인트다. 재발송이 이미 마친 인증을 지우면,
     * 누구든 남의 주소로 요청하는 것만으로 그 사람의 가입을 막을 수 있다.
     * 사용자가 스스로 재발송을 눌렀을 때 자기 인증이 날아가는 문제이기도 하다.
     */
    @Test
    fun `인증을 마친 뒤 같은 주소로 재요청이 들어와도 가입할 수 있다`() {
        val verificationId = requestVerification("resend@example.com")
        confirm(tokenOf(verificationId))

        requestVerification("resend@example.com")

        signUp(verificationId, "resenduser")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.email").value("resend@example.com"))
    }

    /** verificationId 조회 실패(404)와 구분되어야 한다. */
    @Test
    fun `알 수 없는 토큰으로 확인하면 토큰 오류를 알려준다`() {
        mockMvc
            .perform(
                post("/api/v1/email-verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"00000000-0000-0000-0000-000000000000"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"))
    }
}
