package com.example.shopapi.api.auth

import com.example.shopapi.api.auth.application.LoginCommand
import com.example.shopapi.api.auth.application.LoginService
import com.example.shopapi.api.auth.application.TokenReissueService
import com.example.shopapi.api.auth.dto.LoginRequest
import com.example.shopapi.api.auth.dto.RefreshTokenRequest
import com.example.shopapi.api.auth.dto.TokenResponse
import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.support.clientIp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val loginService: LoginService,
    private val tokenReissueService: TokenReissueService,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val tokens =
            loginService.login(
                LoginCommand(request.userId, request.password, httpRequest.clientIp()),
            )
        return ResponseEntity.ok(ApiResponse.of(TokenResponse.from(tokens)))
    }

    /** 액세스 토큰을 다시 받는다. 리프레시 토큰도 함께 새 것으로 바뀐다. */
    @PostMapping("/reissue")
    fun reissue(
        @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val tokens = tokenReissueService.reissue(request.refreshToken)
        return ResponseEntity.ok(ApiResponse.of(TokenResponse.from(tokens)))
    }

    /**
     * 로그아웃. 리프레시 토큰만 무효화한다.
     *
     * 이미 발급된 액세스 토큰은 만료 전까지 계속 유효하다(ADR 0008). 즉시 차단이
     * 필요해지면 별도 장치가 필요하다.
     */
    @PostMapping("/logout")
    fun logout(
        @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<Void> {
        tokenReissueService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
