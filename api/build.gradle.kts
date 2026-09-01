plugins {
    id("shop.spring-boot-application")
}

// 실행 가능한 애플리케이션 모듈. controller / dto / 설정.
dependencies {
    implementation(project(":core:core-domain"))
    runtimeOnly(project(":infrastructure:storage-db"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
