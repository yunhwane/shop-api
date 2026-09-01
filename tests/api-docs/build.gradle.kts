import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    id("shop.kotlin-library")
    alias(libs.plugins.asciidoctor.convert)
}

// REST Docs 로 API 문서를 만드는 테스트 전용 모듈. 프로덕션 코드는 없다.
//
// 앱은 이 문서를 서빙하지 않는다. 서빙하려면 api:bootJar 가 이 모듈의 산출물을 가져가야
// 하는데, 이 모듈은 컨트롤러를 호출하려고 api 에 의존하므로 Gradle 순환이 된다.
// HTML 은 build/docs/asciidoc 에 남고 CI 가 아티팩트로 올린다.

// asciidoctor 실행 시점에만 필요한 확장. 테스트 클래스패스와 섞지 않는다.
val asciidoctorExt = configurations.create("asciidoctorExt")

dependencies {
    // operation:: 매크로를 처리한다. 없으면 문서가 빈 껍데기로 조용히 만들어진다.
    asciidoctorExt("org.springframework.restdocs:spring-restdocs-asciidoctor")

    // api 의 runtimeOnly 어댑터들이 함께 딸려와 컨텍스트가 실제로 뜬다.
    testImplementation(project(":api"))

    // api 는 core-domain 을 implementation 으로 감추므로 소비자에게 보이지 않는다.
    // 인증 토큰은 응답에 담기지 않아(ADR 0002) 포트로 직접 꺼내야 한다.
    testImplementation(project(":core:core-domain"))

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-restdocs")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
}

// 플러그인 기본값(2.5.7)은 spring-restdocs-asciidoctor 가 기대하는 API 와 맞지 않는다.
asciidoctorj {
    setVersion(libs.versions.asciidoctorj.get())
}

// @AutoConfigureRestDocs 의 기본 출력 위치와 같아야 한다.
val snippetsDir = layout.buildDirectory.dir("generated-snippets")

tasks.test {
    outputs.dir(snippetsDir)
}

tasks.named<AsciidoctorTask>("asciidoctor") {
    configurations(asciidoctorExt.name)
    inputs.dir(snippetsDir)
    dependsOn(tasks.test)
    baseDirFollowsSourceFile()
    attributes(mapOf("snippets" to snippetsDir.get().asFile))
}

// 문서는 빌드 산출물이다. ./gradlew build 로 항상 최신이 나오게 한다.
tasks.build {
    dependsOn(tasks.named("asciidoctor"))
}
