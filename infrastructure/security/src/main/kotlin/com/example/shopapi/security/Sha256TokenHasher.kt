package com.example.shopapi.security

import com.example.shopapi.core.domain.port.TokenHasher
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.HexFormat

/**
 * 리프레시 토큰용 해시.
 *
 * bcrypt 를 쓰지 않는 이유는 [TokenHasher] 주석에 있다. 여기서 중요한 것은 결정적이어야
 * 한다는 점이다 - 저장할 때와 조회할 때 같은 값이 나와야 조회 키로 쓸 수 있다.
 * bcrypt 는 매번 다른 솔트를 쓰므로 애초에 이 용도에 맞지 않는다.
 */
@Component
internal class Sha256TokenHasher : TokenHasher {
    override fun hash(raw: String): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()),
        )
}
