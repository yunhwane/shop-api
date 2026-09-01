package com.example.shopapi.architecture

import com.example.shopapi.architecture.Packages.API
import com.example.shopapi.architecture.Packages.ROOT
import com.example.shopapi.architecture.Packages.STORAGE
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.GeneralCodingRules

/**
 * 프레임워크 사용 컨벤션.
 *
 * 리뷰에서 매번 지적하지 않아도 되도록 도구가 잡게 한다.
 */
@AnalyzeClasses(
    packages = [ROOT],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class CodingConventionTest {
    @ArchTest
    val `필드 주입을 쓰지 않는다`: ArchRule =
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("생성자 주입만 쓴다. 필드 주입은 테스트와 불변성을 해친다")

    @ArchTest
    val `표준 출력으로 로그를 남기지 않는다`: ArchRule =
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("println 대신 로거를 쓴다")

    @ArchTest
    val `java_util_logging 을 쓰지 않는다`: ArchRule =
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING

    @ArchTest
    val `Controller 는 api 모듈에만 둔다`: ArchRule =
        classes()
            .that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .or()
            .areAnnotatedWith("org.springframework.stereotype.Controller")
            .should()
            .resideInAPackage(API)
            .because("웹 진입점은 api 모듈이 유일하다")

    @ArchTest
    val `Controller 는 이름이 Controller 로 끝난다`: ArchRule =
        classes()
            .that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should()
            .haveSimpleNameEndingWith("Controller")

    @ArchTest
    val `JPA 엔티티는 storage 모듈에만 둔다`: ArchRule =
        classes()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .resideInAPackage(STORAGE)
            .because("영속성 모델은 도메인 모델과 분리한다")

    @ArchTest
    val `Spring Data 리포지토리는 storage 모듈에만 둔다`: ArchRule =
        classes()
            .that()
            .areAssignableTo("org.springframework.data.repository.Repository")
            .should()
            .resideInAPackage(STORAGE)

    @ArchTest
    val `Controller 는 다른 Controller 를 호출하지 않는다`: ArchRule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Controller")

    @ArchTest
    val `public 가변 필드를 두지 않는다`: ArchRule =
        fields()
            .that()
            .arePublic()
            .and()
            .areNotStatic()
            .should()
            .beFinal()
            .because("외부에서 상태를 직접 바꿀 수 없어야 한다")
}
