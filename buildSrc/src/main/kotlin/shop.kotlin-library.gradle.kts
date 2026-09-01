import org.springframework.boot.gradle.plugin.SpringBootPlugin

/**
 * 모든 모듈이 공유하는 기본 컨벤션.
 * 실행 가능한 모듈은 대신 shop.spring-boot-application 을 적용한다.
 */
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("io.spring.dependency-management")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

// 저장소는 이 컨벤션을 적용한 모든 모듈에 동일하게 걸린다.
// settings 의 dependencyResolutionManagement.repositories 는 아직 @Incubating 이라 쓰지 않는다.
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// Boot BOM 만 import 한다. 각 모듈은 의존성을 버전 없이 선언한다.
dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
