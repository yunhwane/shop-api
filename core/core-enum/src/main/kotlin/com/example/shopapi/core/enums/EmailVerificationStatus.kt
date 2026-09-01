package com.example.shopapi.core.enums

/**
 * 이메일 인증의 진행 상태.
 *
 * 컬럼으로 저장하지 않고 타임스탬프(verifiedAt / consumedAt / expiresAt)와
 * 현재 시각에서 파생한다. 저장된 상태와 타임스탬프가 어긋날 여지를 없앤다.
 */
enum class EmailVerificationStatus {
    /** 메일은 나갔고 아직 링크를 누르지 않았다 */
    PENDING,

    /** 인증됐고 아직 가입에 쓰이지 않았다 */
    VERIFIED,

    /** 기한이 지났다. 다시 발급받아야 한다 */
    EXPIRED,

    /** 가입에 사용됐다. 재사용할 수 없다 */
    CONSUMED,
}
