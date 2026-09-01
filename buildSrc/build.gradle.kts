plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    // ktlint-gradle 는 Maven Central 에 게시되지 않고 Plugin Portal 에만 있다.
    gradlePluginPortal()
}

// 여기 올린 플러그인들을 컨벤션 스크립트(src/main/kotlin/*.gradle.kts)와
// 각 모듈 빌드 스크립트에서 버전 없이 사용할 수 있다.
dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.kotlin.noarg.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.plugin)
    implementation(libs.ktlint.gradle.plugin)
}
