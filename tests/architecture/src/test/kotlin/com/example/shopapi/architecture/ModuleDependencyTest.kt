package com.example.shopapi.architecture

import com.example.shopapi.architecture.Packages.API
import com.example.shopapi.architecture.Packages.CLIENT_MAIL
import com.example.shopapi.architecture.Packages.CLIENT_PAYMENT_TOSS
import com.example.shopapi.architecture.Packages.CORE
import com.example.shopapi.architecture.Packages.CORE_DOMAIN
import com.example.shopapi.architecture.Packages.CORE_ENUM
import com.example.shopapi.architecture.Packages.INFRASTRUCTURE
import com.example.shopapi.architecture.Packages.ROOT
import com.example.shopapi.architecture.Packages.SECURITY
import com.example.shopapi.architecture.Packages.STORAGE
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition

/**
 * 모듈 경계 규칙.
 *
 * Gradle 이 컴파일 타임에 막아주는 것도 있지만(api 는 인프라 모듈을 runtimeOnly 로만 참조),
 * 패키지 단위 규칙과 프레임워크 격리는 빌드 설정만으로는 강제되지 않는다.
 */
@AnalyzeClasses(
    packages = [ROOT],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ModuleDependencyTest {
    @ArchTest
    val `core 는 Spring 에 의존하지 않는다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(CORE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("core 는 프레임워크와 무관한 순수 Kotlin 모듈이어야 한다")

    @ArchTest
    val `core 는 영속성 기술에 의존하지 않는다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(CORE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.persistence..",
                "org.hibernate..",
                "javax.persistence..",
            ).because("도메인 모델이 특정 ORM 에 묶이면 교체가 불가능해진다")

    @ArchTest
    val `core 는 바깥 계층을 모른다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(CORE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*INFRASTRUCTURE, API)
            .because("의존 방향은 항상 안쪽(core)을 향한다")

    @ArchTest
    val `core-enum 은 core-domain 을 모른다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(CORE_ENUM)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CORE_DOMAIN)
            .because("core-enum 은 최하위 모듈이다. 의존은 core-domain -> core-enum 한 방향뿐이다")

    @ArchTest
    val `인프라는 api 를 모른다`: ArchRule =
        noClasses()
            .that()
            .resideInAnyPackage(*INFRASTRUCTURE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(API)
            .because("어댑터는 자신을 쓰는 애플리케이션을 알아서는 안 된다")

    /**
     * 어댑터 하나마다 규칙을 만들어 묶는다. 출발지를 storage 하나로 고정하면 규칙 이름이
     * 말하는 것의 3분의 1만 검사하게 되고, `failOnEmptyShould` 도 storage 가 비어 있지
     * 않은 한 그 사실을 알려주지 않는다.
     *
     * 계층 규칙의 `mayNotBeAccessedByAnyLayer` 와 겹치지만, 이름 있는 규칙 하나가
     * 의도를 더 분명히 남긴다.
     */
    @ArchTest
    val `인프라 어댑터끼리 서로를 모른다`: ArchRule =
        CompositeArchRule
            .of(
                INFRASTRUCTURE.map { origin ->
                    noClasses()
                        .that()
                        .resideInAPackage(origin)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(*INFRASTRUCTURE.filterNot { it == origin }.toTypedArray())
                },
            ).because("어댑터는 서로 독립적으로 교체될 수 있어야 한다")

    @ArchTest
    val `api 는 인프라 구현체를 직접 참조하지 않는다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(API)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*INFRASTRUCTURE)
            .because("api 는 core 의 포트에만 의존한다. 구현체는 런타임에 주입된다")

    @ArchTest
    val `계층 구조를 지킨다`: ArchRule =
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("core-enum")
            .definedBy(CORE_ENUM)
            .layer("core-domain")
            .definedBy(CORE_DOMAIN)
            .layer("storage")
            .definedBy(STORAGE)
            .layer("security")
            .definedBy(SECURITY)
            .layer("client-mail")
            .definedBy(CLIENT_MAIL)
            .layer("client-payment-toss")
            .definedBy(CLIENT_PAYMENT_TOSS)
            .layer("api")
            .definedBy(API)
            .whereLayer("api")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("storage")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("security")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("client-mail")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("client-payment-toss")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("core-domain")
            .mayOnlyBeAccessedByLayers("storage", "security", "client-mail", "client-payment-toss", "api")
            .whereLayer("core-enum")
            .mayOnlyBeAccessedByLayers(
                "core-domain",
                "storage",
                "security",
                "client-mail",
                "client-payment-toss",
                "api",
            )

    @ArchTest
    val `모듈 간 순환 의존이 없다`: ArchRule =
        SlicesRuleDefinition
            .slices()
            .matching("$ROOT.(*)..")
            .should()
            .beFreeOfCycles()
}
