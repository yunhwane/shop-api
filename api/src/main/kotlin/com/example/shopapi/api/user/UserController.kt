package com.example.shopapi.api.user

import com.example.shopapi.api.auth.AuthenticatedUser
import com.example.shopapi.api.common.ApiResponse
import com.example.shopapi.api.user.application.SignUpCommand
import com.example.shopapi.api.user.application.SignUpService
import com.example.shopapi.api.user.application.UserQueryService
import com.example.shopapi.api.user.dto.MeResponse
import com.example.shopapi.api.user.dto.SignUpRequest
import com.example.shopapi.api.user.dto.SignUpResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val signUpService: SignUpService,
    private val userQueryService: UserQueryService,
) {
    @PostMapping
    fun signUp(
        @RequestBody request: SignUpRequest,
    ): ResponseEntity<ApiResponse<SignUpResponse>> {
        val user =
            signUpService.signUp(
                SignUpCommand(
                    verificationId = request.verificationId,
                    userId = request.userId,
                    password = request.password,
                ),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SignUpResponse.from(user)))
    }

    /** 액세스 토큰이 가리키는 회원. 인증이 필요한 첫 엔드포인트다. */
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ApiResponse<MeResponse>> =
        ResponseEntity.ok(ApiResponse.of(MeResponse.from(userQueryService.findMe(principal.id))))
}
