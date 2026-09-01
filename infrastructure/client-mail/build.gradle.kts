plugins {
    id("shop.kotlin-library")
}

// core-domain 의 MailSender / VerificationMailer 포트를 구현한다.
// 전송 수단(Resend, SMTP)과 메일 템플릿을 이 모듈 안에 가둔다.
dependencies {
    implementation(project(":core:core-domain"))

    implementation("org.springframework.boot:spring-boot-starter")

    // RestClient. 웹 서버(tomcat)까지 끌어오지 않도록 starter-web 이 아닌 spring-web 만 쓴다.
    implementation("org.springframework:spring-web")

    // SMTP 구현용 JavaMailSender
    implementation("org.springframework.boot:spring-boot-starter-mail")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
