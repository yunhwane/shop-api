plugins {
    id("shop.kotlin-library")
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    implementation(project(":core:core-domain"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
