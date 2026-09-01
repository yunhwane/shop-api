package com.example.shopapi.architecture

/**
 * 모듈과 패키지의 대응. 규칙에서 문자열을 반복하지 않도록 한 곳에 모은다.
 *
 * 새 모듈을 추가하면 여기에 상수를 넣고 [ModuleDependencyTest] 의 계층 규칙에 등록한다.
 */
object Packages {
    const val ROOT = "com.example.shopapi"

    /** core:core-enum */
    const val CORE_ENUM = "$ROOT.core.enums.."

    /** core:core-domain */
    const val CORE_DOMAIN = "$ROOT.core.domain.."

    /** core 전체 */
    const val CORE = "$ROOT.core.."

    /** infrastructure:storage-db */
    const val STORAGE = "$ROOT.storage.."

    /** api */
    const val API = "$ROOT.api.."
}
