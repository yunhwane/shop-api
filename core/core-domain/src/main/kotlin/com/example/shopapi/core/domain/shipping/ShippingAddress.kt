package com.example.shopapi.core.domain.shipping

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.reconstituting

/**
 * 배송지. 주문할 때 한 번 입력받아 그대로 보관한다(ADR 0020).
 *
 * 필드가 각각 자기 값 객체의 검증을 이미 통과했으므로 조합에 대한 추가 검증이 없어
 * 별도의 `of`/`reconstitute` 를 두지 않는다 - [OrderLine][com.example.shopapi.core.domain.order.OrderLine]
 * 과 같은 이유다(ADR 0007).
 */
data class ShippingAddress(
    val recipientName: RecipientName,
    val phone: PhoneNumber,
    val postalCode: PostalCode,
    val addressLine1: AddressLine1,
    val addressLine2: AddressLine2?,
)

/** 수령인 이름 */
@JvmInline
value class RecipientName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 50

        private val CONTROL_CHARACTER = Regex("\\p{Cntrl}")

        fun of(raw: String): RecipientName {
            val trimmed = raw.trim()
            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
                throw InvalidValueException(FIELD, "${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.")
            }
            if (CONTROL_CHARACTER.containsMatchIn(trimmed)) {
                throw InvalidValueException(FIELD, "줄바꿈이나 제어 문자를 쓸 수 없습니다.")
            }
            return RecipientName(trimmed)
        }

        fun reconstitute(stored: String): RecipientName = reconstituting(FIELD) { of(stored) }

        private const val FIELD = "recipientName"
    }
}

/**
 * 연락처.
 *
 * 하이픈을 떼거나 붙이는 정규화를 하지 않는다 - `ProductName` 과 같은 이유로, 유일성
 * 판단에 쓰이지 않는 값의 표기를 서버가 바꿀 이유가 없다. 국가번호(`+82`)는 받지 않는다.
 */
@JvmInline
value class PhoneNumber private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[0-9]{2,4}-?[0-9]{3,4}-?[0-9]{4}$")

        fun of(raw: String): PhoneNumber {
            val trimmed = raw.trim()
            if (!PATTERN.matches(trimmed)) {
                throw InvalidValueException(FIELD, "숫자와 하이픈으로 된 연락처여야 합니다.")
            }
            return PhoneNumber(trimmed)
        }

        fun reconstitute(stored: String): PhoneNumber = reconstituting(FIELD) { of(stored) }

        private const val FIELD = "phone"
    }
}

/** 우편번호. 2015년에 바뀐 5자리 체계만 받는다 - 6자리 구 우편번호는 더 이상 쓰이지 않는다 */
@JvmInline
value class PostalCode private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val LENGTH = 5

        private val PATTERN = Regex("^[0-9]{$LENGTH}$")

        fun of(raw: String): PostalCode {
            val trimmed = raw.trim()
            if (!PATTERN.matches(trimmed)) {
                throw InvalidValueException(FIELD, "${LENGTH}자리 숫자여야 합니다.")
            }
            return PostalCode(trimmed)
        }

        fun reconstitute(stored: String): PostalCode = reconstituting(FIELD) { of(stored) }

        private const val FIELD = "postalCode"
    }
}

/** 기본주소(도로명·지번) */
@JvmInline
value class AddressLine1 private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 200

        private val CONTROL_CHARACTER = Regex("\\p{Cntrl}")

        fun of(raw: String): AddressLine1 {
            val trimmed = raw.trim()
            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
                throw InvalidValueException(FIELD, "${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.")
            }
            if (CONTROL_CHARACTER.containsMatchIn(trimmed)) {
                throw InvalidValueException(FIELD, "줄바꿈이나 제어 문자를 쓸 수 없습니다.")
            }
            return AddressLine1(trimmed)
        }

        fun reconstitute(stored: String): AddressLine1 = reconstituting(FIELD) { of(stored) }

        private const val FIELD = "addressLine1"
    }
}

/**
 * 상세주소(동·호수).
 *
 * 없을 수 있는 값이라 호출자가 `null` 을 그대로 넘긴다 - 빈 문자열을 받아 `null` 로
 * 바꿔 주지 않는다. "입력하지 않았다" 와 "빈 값을 입력했다" 를 여기서 섞으면, 그 판단이
 * 값 객체 안에 숨는다.
 */
@JvmInline
value class AddressLine2 private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 100

        private val CONTROL_CHARACTER = Regex("\\p{Cntrl}")

        fun of(raw: String): AddressLine2 {
            val trimmed = raw.trim()
            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
                throw InvalidValueException(FIELD, "${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다.")
            }
            if (CONTROL_CHARACTER.containsMatchIn(trimmed)) {
                throw InvalidValueException(FIELD, "줄바꿈이나 제어 문자를 쓸 수 없습니다.")
            }
            return AddressLine2(trimmed)
        }

        fun reconstitute(stored: String): AddressLine2 = reconstituting(FIELD) { of(stored) }

        private const val FIELD = "addressLine2"
    }
}
