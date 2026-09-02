plugins {
    id("shop.kotlin-library")
}

// 아키텍처 규칙만 검증하는 테스트 전용 모듈. 프로덕션 코드는 없다.
//
// 모듈 경계 규칙을 검사하려면 전 모듈의 클래스가 한 클래스패스에 있어야 한다.
// 그래서 이 모듈만 예외적으로 모든 모듈을 참조한다. 반대로 이 모듈을 참조하는
// 곳은 없어야 한다(tests/* 는 항상 리프 모듈이다).
dependencies {
    testImplementation(project(":core:core-enum"))
    testImplementation(project(":core:core-domain"))
    testImplementation(project(":infrastructure:storage-db"))
    testImplementation(project(":infrastructure:security"))
    testImplementation(project(":infrastructure:client-mail"))
    testImplementation(project(":infrastructure:client-payment-toss"))
    testImplementation(project(":api"))

    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
