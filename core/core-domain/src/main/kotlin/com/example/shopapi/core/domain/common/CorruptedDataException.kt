package com.example.shopapi.core.domain.common

import com.example.shopapi.core.enums.ErrorCode

/**
 * 저장소에서 읽어온 값이 도메인 규칙을 어긴다.
 *
 * 입력 검증 실패와 같은 예외를 쓰면 안 된다. 서버 데이터 문제가 `400 INVALID_REQUEST` 로
 * 나가면서 **클라이언트가 보내지도 않은 필드를 탓하게** 되고, 4xx 는 warn 으로 남아
 * 에러 로그에도 걸리지 않는다. 고쳐야 할 사람이 아무도 그 사실을 모른다.
 *
 * 메시지에 값 자체를 담지 않는다. 어긴 필드 이름만으로 충분하고, 값에는 해시나 토큰이
 * 섞일 수 있다.
 */
class CorruptedDataException(
    val field: String,
    override val cause: InvalidValueException,
) : DomainException(ErrorCode.INTERNAL_ERROR, "저장된 $field 값이 도메인 규칙을 어긴다")

/**
 * 저장된 값을 값 객체로 되돌린다.
 *
 * 검증은 입력 경로와 똑같이 한다. 건너뛰면 규칙을 어기는 값이 도메인 안으로 조용히
 * 흘러들어 값 객체가 보장한다고 말하는 불변식이 거짓이 된다. 바꾸는 것은 실패의 의미뿐이다.
 */
internal inline fun <T> reconstituting(
    field: String,
    build: () -> T,
): T =
    try {
        build()
    } catch (e: InvalidValueException) {
        throw CorruptedDataException(field, e)
    }
