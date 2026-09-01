package com.example.shopapi.architecture

/**
 * 모듈과 패키지의 대응. 규칙에서 문자열을 반복하지 않도록 한 곳에 모은다.
 *
 * 새 모듈을 추가하면 여기에 상수를 넣고 [ModuleDependencyTest] 의 계층 규칙에 등록한다.
 */
object Packages {
    const val ROOT = "com.example.shopapi"

    const val CORE_ENUM = "$ROOT.core.enums.."
    const val CORE_DOMAIN = "$ROOT.core.domain.."
    const val CORE = "$ROOT.core.."
    const val API = "$ROOT.api.."

    /** infrastructure:storage-db */
    const val STORAGE = "$ROOT.storage.."

    /** infrastructure:security */
    const val SECURITY = "$ROOT.security.."

    /** infrastructure:client-mail */
    const val CLIENT_MAIL = "$ROOT.client.mail.."

    /**
     * 인프라 어댑터 전체.
     *
     * api 는 이들 중 어느 것도 컴파일 타임에 참조하지 않는다. 새 어댑터 모듈을 만들면
     * 여기에 추가해야 규칙이 함께 걸린다.
     */
    val INFRASTRUCTURE = arrayOf(STORAGE, SECURITY, CLIENT_MAIL)
}
