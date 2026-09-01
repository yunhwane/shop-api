package com.example.shopapi.apidocs

import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.VerificationId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 로그인과 토큰 회전의 API 문서를 만든다. 동작 검증은 api 모듈의 `LoginFlowTest` 가 맡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(properties = ["mail.provider=log", "security.bcrypt.strength=4"])
class LoginDocsTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
) {
    @Test
    fun `로그인한다`() {
        signUp("docslogin1", "docs-login1@example.com")

        login("docslogin1")
            .andExpect(status().isOk)
            .andDo(
                document(
                    "auth-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("userId").description("로그인 아이디"),
                        fieldWithPath("password").description("비밀번호"),
                    ),
                    responseFields(tokenFields()),
                ),
            )
    }

    @Test
    fun `액세스 토큰을 재발급한다`() {
        signUp("docslogin2", "docs-login2@example.com")
        val refreshToken = extract(login("docslogin2").andReturn().response.contentAsString, "refreshToken")

        reissue(refreshToken)
            .andExpect(status().isOk)
            .andDo(
                document(
                    "auth-reissue",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("refreshToken")
                            .description("직전에 받은 리프레시 토큰. 이 요청으로 소비되고 새 값이 나온다"),
                    ),
                    responseFields(tokenFields()),
                ),
            )
    }

    @Test
    fun `로그아웃한다`() {
        signUp("docslogin3", "docs-login3@example.com")
        val refreshToken = extract(login("docslogin3").andReturn().response.contentAsString, "refreshToken")

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"$refreshToken"}"""),
            ).andExpect(status().isNoContent)
            .andDo(
                document(
                    "auth-logout",
                    preprocessRequest(prettyPrint()),
                    requestFields(
                        fieldWithPath("refreshToken").description("무효화할 리프레시 토큰"),
                    ),
                ),
            )
    }

    @Test
    fun `내 정보를 조회한다`() {
        signUp("docslogin4", "docs-login4@example.com")
        val accessToken = extract(login("docslogin4").andReturn().response.contentAsString, "accessToken")

        mockMvc
            .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andDo(
                document(
                    "user-me",
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName(HttpHeaders.AUTHORIZATION).description("`Bearer {accessToken}`"),
                    ),
                    responseFields(
                        fieldWithPath("data.id").description("회원 식별자"),
                        fieldWithPath("data.userId").description("로그인 아이디"),
                        fieldWithPath("data.email").description("가입 시 인증한 이메일"),
                        fieldWithPath("data.status").description("ACTIVE / SUSPENDED / WITHDRAWN"),
                    ),
                ),
            )
    }

    @Test
    fun `토큰 없이 접근하면 거부한다`() {
        mockMvc
            .perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
            .andDo(
                document(
                    "error-unauthenticated",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("type").description("문제 유형 URI"),
                        fieldWithPath("title").description("사람이 읽는 요약"),
                        fieldWithPath("status").description("HTTP 상태 코드"),
                        fieldWithPath("detail").description("설명. 실패 이유는 구분해 주지 않는다"),
                        fieldWithPath("instance").description("요청 경로"),
                        fieldWithPath("code").description("UNAUTHENTICATED"),
                        fieldWithPath("timestamp").description("발생 시각"),
                    ),
                ),
            )
    }

    private fun tokenFields() =
        listOf(
            fieldWithPath("data.accessToken").description("`Authorization: Bearer` 로 보낼 JWT"),
            fieldWithPath("data.accessTokenExpiresAt").description("액세스 토큰 만료 시각"),
            fieldWithPath("data.refreshToken")
                .description("재발급용. **매번 바뀌므로 응답의 새 값을 반드시 저장한다**"),
            fieldWithPath("data.refreshTokenExpiresAt").description("리프레시 토큰 만료 시각"),
        )

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
                ).andReturn()
                .response.contentAsString
        val verificationId = extract(issued, "verificationId")

        val verification = verifications.findByVerificationId(VerificationId.of(verificationId))
        assertNotNull(verification)

        mockMvc.perform(
            post("/api/v1/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${verification.token.value}"}"""),
        )
        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"$verificationId","userId":"$userId","password":"$PASSWORD"}"""),
        )
    }

    private fun extract(
        body: String,
        field: String,
    ): String = Regex("\"$field\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    private companion object {
        const val PASSWORD = "password1"
    }
}
