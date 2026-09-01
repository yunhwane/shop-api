package com.example.shopapi.apidocs

import com.example.shopapi.core.domain.port.EmailVerificationRepository
import com.example.shopapi.core.domain.verification.VerificationId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 회원가입 흐름의 API 문서를 만든다.
 *
 * 이 테스트는 동작 검증이 목적이 아니다. 그쪽은 api 모듈의 `SignUpFlowTest` 가 맡는다.
 * 여기서는 **요청과 응답의 각 필드가 무엇인지**를 적는 데 집중한다. 다만 실제 요청을
 * 보내 만든 스니펫이라, 문서와 구현이 어긋나면 이 테스트가 먼저 깨진다.
 *
 * 토큰은 메일함이 아니라 저장소 포트에서 꺼낸다. 응답에 담기지 않기 때문이다(ADR 0002).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(
    properties = [
        "mail.provider=log",
        // 이 테스트들이 검증하는 것은 호출 제한이 아니다. RateLimitTest 가 따로 본다.
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.rate-limit.login-failure-per-ip.limit=1000",
    ],
)
class SignUpDocsTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val verifications: EmailVerificationRepository,
) {
    @Test
    fun `인증 메일 발송을 요청한다`() {
        requestVerification("docs-request@example.com")
            .andExpect(status().isCreated)
            .andDo(
                document(
                    "email-verification-request",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("email").description("인증받을 이메일 주소. 이미 가입된 주소면 409"),
                    ),
                    responseFields(
                        fieldWithPath("data.verificationId")
                            .description("이후 단계에서 클라이언트가 들고 다닐 식별자. 토큰과 달리 비밀이 아니다"),
                        fieldWithPath("data.expiresAt").description("메일 링크의 만료 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `메일 링크로 인증을 완료한다`() {
        val verificationId = issuedVerificationId("docs-confirm@example.com")

        confirm(tokenOf(verificationId))
            .andExpect(status().isOk)
            .andDo(
                document(
                    "email-verification-confirm",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("token")
                            .description("메일 링크의 쿼리스트링에 담긴 값. 메일함에만 존재한다"),
                    ),
                    responseFields(
                        fieldWithPath("data.status").description("항상 VERIFIED. 실패는 예외로 나간다"),
                    ),
                ),
            )
    }

    @Test
    fun `인증 상태를 조회한다`() {
        val verificationId = issuedVerificationId("docs-status@example.com")

        mockMvc
            .perform(get("/api/v1/email-verifications/{verificationId}", verificationId))
            .andExpect(status().isOk)
            .andDo(
                document(
                    "email-verification-status",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("verificationId").description("발송 요청 응답으로 받은 식별자"),
                    ),
                    responseFields(
                        fieldWithPath("data.status")
                            .description("PENDING / VERIFIED / EXPIRED / CONSUMED"),
                    ),
                ),
            )
    }

    @Test
    fun `인증을 마친 뒤 가입한다`() {
        val verificationId = issuedVerificationId("docs-signup@example.com")
        confirm(tokenOf(verificationId)).andExpect(status().isOk)

        signUp(verificationId, "docsuser01")
            .andExpect(status().isCreated)
            .andDo(
                document(
                    "user-sign-up",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("verificationId").description("인증을 마친 절차의 식별자"),
                        fieldWithPath("userId").description("로그인 아이디. 영문과 숫자 4~20자"),
                        fieldWithPath("password").description("8~64자. 영문과 숫자를 각각 하나 이상"),
                    ),
                    responseFields(
                        fieldWithPath("data.id").description("회원 식별자"),
                        fieldWithPath("data.userId").description("소문자로 정규화되어 저장된 아이디"),
                        fieldWithPath("data.email")
                            .description("요청에 없던 값이다. 인증 레코드에서 가져온다"),
                    ),
                ),
            )
    }

    @Test
    fun `요청 값이 형식을 어기면 어느 필드인지 알려준다`() {
        val verificationId = issuedVerificationId("docs-invalid@example.com")
        confirm(tokenOf(verificationId)).andExpect(status().isOk)

        signUp(verificationId, userId = "ab")
            .andExpect(status().isBadRequest)
            .andDo(
                document(
                    "error-invalid-request",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("type").description("문제 유형 URI. 코드에서 유도된다"),
                        fieldWithPath("title").description("사람이 읽는 요약"),
                        fieldWithPath("status").description("HTTP 상태 코드"),
                        fieldWithPath("detail").description("이 요청에 한정된 설명"),
                        fieldWithPath("instance").description("요청 경로"),
                        fieldWithPath("code").description("클라이언트가 분기할 기계 판독용 심볼"),
                        fieldWithPath("timestamp").description("발생 시각"),
                        fieldWithPath("errors[].field").description("규칙을 어긴 필드"),
                        fieldWithPath("errors[].reason").description("사유. 입력값 자체는 담지 않는다"),
                    ),
                ),
            )
    }

    @Test
    fun `이미 쓰이는 아이디면 충돌을 알려준다`() {
        val taken = issuedVerificationId("docs-taken1@example.com")
        confirm(tokenOf(taken)).andExpect(status().isOk)
        signUp(taken, "takenuser1").andExpect(status().isCreated)

        val second = issuedVerificationId("docs-taken2@example.com")
        confirm(tokenOf(second)).andExpect(status().isOk)

        signUp(second, "takenuser1")
            .andExpect(status().isConflict)
            .andDo(
                document(
                    "error-duplicate-user-id",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("type").description("문제 유형 URI"),
                        fieldWithPath("title").description("사람이 읽는 요약"),
                        fieldWithPath("status").description("HTTP 상태 코드"),
                        fieldWithPath("detail").description("이 요청에 한정된 설명"),
                        fieldWithPath("instance").description("요청 경로"),
                        fieldWithPath("code").description("DUPLICATE_USER_ID"),
                        fieldWithPath("timestamp").description("발생 시각"),
                    ),
                ),
            )
    }

    private fun requestVerification(email: String): ResultActions =
        mockMvc.perform(
            post("/api/v1/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        )

    private fun issuedVerificationId(email: String): String {
        val body = requestVerification(email).andReturn().response.contentAsString
        return Regex("\"verificationId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun tokenOf(verificationId: String): String {
        val verification = verifications.findByVerificationId(VerificationId.of(verificationId))
        assertNotNull(verification)
        return verification.token.value
    }

    private fun confirm(token: String): ResultActions =
        mockMvc.perform(
            post("/api/v1/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token"}"""),
        )

    private fun signUp(
        verificationId: String,
        userId: String,
        password: String = "password1",
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationId":"$verificationId","userId":"$userId","password":"$password"}"""),
        )
}
