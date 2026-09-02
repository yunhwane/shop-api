plugins {
    id("shop.kotlin-library")
}

// core-domain 의 PaymentGateway 포트를 구현한다. Toss Payments 호출을 이 모듈 안에 가둔다.
dependencies {
    implementation(project(":core:core-domain"))

    implementation("org.springframework.boot:spring-boot-starter")

    // RestClient. 웹 서버(tomcat)까지 끌어오지 않도록 starter-web 이 아닌 spring-web 만 쓴다.
    implementation("org.springframework:spring-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
