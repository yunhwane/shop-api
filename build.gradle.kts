// 루트 프로젝트는 빌드 설정을 갖지 않는다.
// 공통 설정은 buildSrc 의 컨벤션 플러그인(shop.*)에 있다.
//
//   api ──implementation──> core:core-domain ──api──> core:core-enum
//    └───runtimeOnly──────> infrastructure:storage-db ──implementation──> core:core-domain
//
// 여기서 ktlint 를 적용하는 이유는 루트의 .kts 스크립트도 검사 대상에 넣기 위해서다.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
}

ktlint {
    // ktlint / ktlint-gradle 두 버전이 있어 접근자가 그룹이 된다. asProvider() 로 ktlint 자신을 꺼낸다.
    version.set(libs.versions.ktlint.asProvider())
}
