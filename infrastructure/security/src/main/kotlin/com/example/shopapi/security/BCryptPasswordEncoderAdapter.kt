package com.example.shopapi.security

import com.example.shopapi.core.domain.port.PasswordEncoder
import com.example.shopapi.core.domain.user.EncodedPassword
import com.example.shopapi.core.domain.user.RawPassword
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder as SpringBCryptPasswordEncoder

/**
 * bcrypt 구현. 도메인의 [PasswordEncoder] 와 Spring Security 의 동명 인터페이스가
 * 이름이 같아 임포트 별칭으로 구분한다.
 *
 * strength 를 프로퍼티로 뺀 이유는 테스트 때문이다. bcrypt 는 의도적으로 느려서
 * 기본값 10 으로 해싱하면 테스트 한 건당 수십 ms 가 든다.
 */
@Component
internal class BCryptPasswordEncoderAdapter(
    @Value("\${security.bcrypt.strength:10}") strength: Int,
) : PasswordEncoder {
    private val delegate = SpringBCryptPasswordEncoder(strength)

    // Spring Security 는 encode 의 반환을 nullable 로 선언한다. 실제로 null 이 나오면
    // 비밀번호 없는 계정이 만들어지므로 여기서 끊는다.
    override fun encode(raw: RawPassword): EncodedPassword =
        EncodedPassword.of(
            requireNotNull(delegate.encode(raw.value)) { "비밀번호 인코딩 결과가 null 이다" },
        )

    override fun matches(
        raw: RawPassword,
        encoded: EncodedPassword,
    ): Boolean = delegate.matches(raw.value, encoded.value)
}
