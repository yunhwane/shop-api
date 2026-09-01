package com.example.shopapi.architecture

import com.example.shopapi.architecture.Packages.API
import com.example.shopapi.architecture.Packages.CORE_DOMAIN
import com.example.shopapi.architecture.Packages.ROOT
import com.example.shopapi.architecture.Packages.STORAGE
import com.tngtech.archunit.core.domain.JavaCall.Predicates.target
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith
import com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameStartingWith
import com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner
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

    /**
     * 대상을 우리 패키지로 좁힌 이유가 있다. `@RestController` **애노테이션 클래스의 이름 자체가**
     * `Controller` 로 끝나서, 이름만으로 판별하면 모든 컨트롤러가 자기 애노테이션에 의존한다는
     * 이유로 위반이 된다. 컨트롤러가 없던 동안에는 검사 대상이 0건이라 드러나지 않았다.
     */
    @ArchTest
    val `Controller 는 다른 Controller 를 호출하지 않는다`: ArchRule =
        noClasses()
            .that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should()
            .dependOnClassesThat(
                simpleNameEndingWith("Controller").and(resideInAPackage(API)),
            ).because("컨트롤러끼리 호출하면 하나의 요청 흐름이 두 곳으로 갈라진다")

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
            // Kotlin 프로퍼티는 private 필드 + 접근자로 컴파일되므로 검사 대상이 0건이다.
            // 앞으로 들어올 Java 코드나 @JvmField 를 막는 예방 규칙이라 빈 상태를 허용한다.
            .allowEmptyShould(true)

    /**
     * 값 객체의 `reconstitute` 는 검증 실패를 `INVALID_REQUEST` 가 아니라 `INTERNAL_ERROR` 로
     * 바꾼다. 저장된 데이터가 깨진 경우를 위한 통로다. 입력 경로에서 쓰면 클라이언트가 보낸
     * 잘못된 값이 400 대신 500 으로 나가고, 사용자는 무엇을 고쳐야 하는지 알 수 없게 된다.
     */
    @ArchTest
    val `복원 팩토리는 storage 모듈에서만 호출한다`: ArchRule =
        noClasses()
            .that()
            .resideOutsideOfPackage(STORAGE)
            .should()
            .callMethodWhere(
                target(nameStartingWith("reconstitute")).and(target(owner(resideInAPackage(CORE_DOMAIN)))),
            ).because("검증을 통과한 입력과 이미 저장된 값은 실패했을 때 의미가 다르다")
}
