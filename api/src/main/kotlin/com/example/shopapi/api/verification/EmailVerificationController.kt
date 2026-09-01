package com.example.shopapi.api.verification

import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.support.clientIp
import com.example.shopapi.api.verification.application.EmailVerificationService
import com.example.shopapi.api.verification.dto.VerificationConfirmRequest
import com.example.shopapi.api.verification.dto.VerificationIssuedResponse
import com.example.shopapi.api.verification.dto.VerificationRequest
import com.example.shopapi.api.verification.dto.VerificationStatusResponse
import com.example.shopapi.core.enums.EmailVerificationStatus
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/email-verifications")
class EmailVerificationController(
    private val service: EmailVerificationService,
) {
    /** 인증 메일을 보낸다. 응답의 verificationId 를 클라이언트가 보관한다. */
    @PostMapping
    fun request(
        @RequestBody request: VerificationRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<VerificationIssuedResponse>> {
        val verification = service.request(request.email, httpRequest.clientIp())
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.of(VerificationIssuedResponse.from(verification)))
    }

    /** 인증을 완료한다. 메일 링크가 아니라 프론트엔드 `/verify` 페이지가 호출한다(ADR 0002). */
    @PostMapping("/confirm")
    fun confirm(
        @RequestBody request: VerificationConfirmRequest,
    ): ResponseEntity<ApiResponse<VerificationStatusResponse>> {
        service.confirm(request.token)
        // 여기 도달했다면 인증은 성공했다. 실패는 전부 예외로 빠진다.
        return ResponseEntity.ok(ApiResponse.of(VerificationStatusResponse(EmailVerificationStatus.VERIFIED)))
    }

    /** 가입 폼이 인증 완료를 감지하기 위해 폴링한다. */
    @GetMapping("/{verificationId}")
    fun status(
        @PathVariable verificationId: String,
    ): ResponseEntity<ApiResponse<VerificationStatusResponse>> =
        ResponseEntity.ok(ApiResponse.of(VerificationStatusResponse(service.statusOf(verificationId))))
}
