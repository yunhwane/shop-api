plugins {
    id("shop.kotlin-library")
}

// 도메인 모델, 도메인 서비스, 포트(인터페이스)를 둔다.
// 프레임워크/영속성에 의존하지 않는다.
dependencies {
    api(project(":core:core-enum"))
}
