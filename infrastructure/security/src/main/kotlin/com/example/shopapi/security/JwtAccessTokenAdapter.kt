package com.example.shopapi.security

import com.example.shopapi.core.domain.port.AccessToken
import com.example.shopapi.core.domain.port.AccessTokenIssuer
import com.example.shopapi.core.domain.port.AccessTokenParser
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/**
 * HS256 으로 서명한 JWT 를 액세스 토큰으로 쓴다.
 *
 * 발급과 해석을 한 클래스가 하지만 포트는 둘로 나뉘어 있다. 로그인 유스케이스는 발급만,
 * 인증 필터는 해석만 알면 된다.
 */
@Component
internal class JwtAccessTokenAdapter(
    private val properties: JwtProperties,
) : AccessTokenIssuer,
    AccessTokenParser {
    private val secretKey = resolveSecretKey()

    private val encoder =
        NimbusJwtEncoder(
            ImmutableJWKSet<SecurityContext>(
                JWKSet(OctetSequenceKey.Builder(secretKey).algorithm(JWSAlgorithm.HS256).build()),
            ),
        )

    private val decoder =
        NimbusJwtDecoder
            .withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

    override fun issue(
        userId: Long,
        now: Instant,
    ): AccessToken {
        val expiresAt = now + properties.accessTokenTtl
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return AccessToken(
            value = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            expiresAt = expiresAt,
        )
    }

    /**
     * 서명 오류와 만료를 구분하지 않고 null 로 돌려준다. 인증 필터에서 둘은 같은 결론이고,
     * 만료는 정상 흐름에서 늘 일어난다.
     */
    override fun parseUserId(token: String): Long? =
        try {
            decoder.decode(token).subject?.toLongOrNull()
        } catch (e: JwtException) {
            log.debug("액세스 토큰 해석 실패", e)
            null
        }

    private fun resolveSecretKey(): SecretKeySpec {
        if (properties.secret.isBlank()) {
            // 조용히 넘어가면 안 된다. 이 상태로 운영에 올라가면 재시작마다 전원 로그아웃된다.
            log.warn(
                "security.jwt.secret 이 비어 있어 임의의 키를 생성했다. " +
                    "재시작하면 발급된 모든 액세스 토큰이 무효가 된다. 운영에서는 반드시 설정한다.",
            )
            return SecretKeySpec(ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }, HMAC_ALGORITHM)
        }

        val bytes = properties.secret.toByteArray()
        require(bytes.size >= KEY_BYTES) {
            "security.jwt.secret 은 최소 ${KEY_BYTES}바이트여야 한다. HS256 이 요구하는 길이다"
        }
        return SecretKeySpec(bytes, HMAC_ALGORITHM)
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"

        /** HS256 의 최소 키 길이 */
        private const val KEY_BYTES = 32

        private val log = LoggerFactory.getLogger(JwtAccessTokenAdapter::class.java)
    }
}
