plugins {
    id("shop.kotlin-library")
}

// core-domain 의 PasswordEncoder / TokenGenerator 포트를 구현한다.
// 해싱 알고리즘 교체가 이 모듈 안에서 끝나도록 api 는 이 모듈을 runtimeOnly 로만 참조한다.
dependencies {
    implementation(project(":core:core-domain"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.security:spring-security-crypto")

    // JWT 서명과 검증. Spring Security BOM 이 관리한다.
    implementation("org.springframework.security:spring-security-oauth2-jose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
