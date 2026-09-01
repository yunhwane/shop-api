package com.example.shopapi.api

import com.example.shopapi.core.domain.common.CorruptedDataException
import com.example.shopapi.core.domain.port.UserRepository
import com.example.shopapi.core.domain.user.UserId
import com.example.shopapi.core.enums.ErrorCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 깨진 행을 읽는 경로를 실제 DB 로 확인한다.
 *
 * 값 객체를 거치지 않고 JDBC 로 직접 넣는다. 애플리케이션 경로로는 만들 수 없는 상태라서다.
 * 마이그레이션이나 손으로 넣은 데이터가 만들어 내는 상황이 이것이다.
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "mail.provider=log",
        // 이 테스트들이 검증하는 것은 호출 제한이 아니다. RateLimitTest 가 따로 본다.
        "security.rate-limit.verification-per-ip.limit=1000",
        "security.rate-limit.verification-per-email.limit=1000",
        "security.rate-limit.login-failure-per-ip.limit=1000",
    ],
)
class CorruptedRowTest(
    @param:Autowired private val dataSource: DataSource,
    @param:Autowired private val users: UserRepository,
) {
    @Test
    fun `형식을 어기는 행을 읽으면 클라이언트가 아니라 서버를 탓한다`() {
        execute(
            """
            INSERT INTO users (user_id, email, password, status, created_at)
            VALUES ('corrupt1', 'not-an-email', 'hash', 'ACTIVE', CURRENT_TIMESTAMP)
            """.trimIndent(),
        )

        try {
            val exception =
                assertFailsWith<CorruptedDataException> { users.findByUserId(UserId.of("corrupt1")) }

            // INVALID_REQUEST 였다면 400 으로 나가면서, 가입 요청이 받지도 않는 email 필드를
            // 클라이언트 탓으로 돌리고 4xx 라 에러 로그에도 걸리지 않는다.
            assertEquals(ErrorCode.INTERNAL_ERROR, exception.errorCode)
            assertEquals("email", exception.field)
        } finally {
            execute("DELETE FROM users WHERE user_id = 'corrupt1'")
        }
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }
    }
}
