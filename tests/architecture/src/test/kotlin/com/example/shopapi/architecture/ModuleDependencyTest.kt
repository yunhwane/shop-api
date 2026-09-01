package com.example.shopapi.architecture

import com.example.shopapi.architecture.Packages.API
import com.example.shopapi.architecture.Packages.CORE
import com.example.shopapi.architecture.Packages.CORE_DOMAIN
import com.example.shopapi.architecture.Packages.CORE_ENUM
import com.example.shopapi.architecture.Packages.ROOT
import com.example.shopapi.architecture.Packages.STORAGE
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition

/**
 * 모듈 경계 규칙.
 *
 * Gradle 이 컴파일 타임에 막아주는 것도 있지만(api 는 storage-db 를 runtimeOnly 로만 참조),
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
            .resideInAnyPackage(STORAGE, API)
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
    val `infrastructure 는 api 를 모른다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(STORAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(API)
            .because("어댑터는 자신을 쓰는 애플리케이션을 알아서는 안 된다")

    @ArchTest
    val `api 는 영속성 구현체를 직접 참조하지 않는다`: ArchRule =
        noClasses()
            .that()
            .resideInAPackage(API)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(STORAGE)
            .because("api 는 core 의 포트에만 의존한다. 구현체는 런타임에 주입된다")

    @ArchTest
    val `계층 구조를 지킨다`: ArchRule =
        // 모듈이 아직 비어 있어 optionalLayer 를 쓴다.
        // 각 모듈에 클래스가 생기면 layer 로 바꿔서 "빈 계층"도 실패로 잡는 것을 권장한다.
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .optionalLayer("core-enum")
            .definedBy(CORE_ENUM)
            .optionalLayer("core-domain")
            .definedBy(CORE_DOMAIN)
            .optionalLayer("infrastructure")
            .definedBy(STORAGE)
            .optionalLayer("api")
            .definedBy(API)
            .whereLayer("api")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("core-domain")
            .mayOnlyBeAccessedByLayers("infrastructure", "api")
            .whereLayer("core-enum")
            .mayOnlyBeAccessedByLayers("core-domain", "infrastructure", "api")

    @ArchTest
    val `모듈 간 순환 의존이 없다`: ArchRule =
        SlicesRuleDefinition
            .slices()
            .matching("$ROOT.(*)..")
            .should()
            .beFreeOfCycles()
}
