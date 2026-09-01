plugins {
    id("shop.spring-boot-application")
}

// 실행 가능한 애플리케이션 모듈. controller / dto / 유스케이스 / 설정.
//
// 인프라 모듈은 전부 runtimeOnly 다. 구현체를 컴파일 타임에 import 할 수 없고,
// 빈은 컴포넌트 스캔으로 런타임에 주입된다. 구현이 안 보이는 것은 의도다(ADR 0004).
dependencies {
    implementation(project(":core:core-domain"))
    runtimeOnly(project(":infrastructure:storage-db"))
    runtimeOnly(project(":infrastructure:security"))
    runtimeOnly(project(":infrastructure:client-mail"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // 유스케이스의 트랜잭션 경계에 @Transactional 이 필요하다(ADR 0003).
    // 구현(JPA TransactionManager)은 storage-db 가 런타임에 제공하므로 애노테이션만 가져온다.
    implementation("org.springframework:spring-tx")

    // 인증 필터 체인은 웹 관심사라 api 가 갖는다. 해싱과 토큰 서명 구현은
    // 여전히 infrastructure:security 안에 있고 api 는 그것을 보지 못한다(ADR 0004).
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
