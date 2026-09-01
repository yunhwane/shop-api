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
@TestPropertySource(properties = ["mail.provider=log"])
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
}
