package com.example.shopapi.api.product

import com.example.shopapi.api.product.application.ProductManagementService
import com.example.shopapi.api.product.application.ProductRegistrationService
import com.example.shopapi.api.product.application.RegisterProductCommand
import com.example.shopapi.core.enums.ProductCategory
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 로컬 실행용 카탈로그 시드.
 *
 * 상품 쓰기 API 가 없고 저장소가 H2 인메모리 + `create-drop` 이라, 조회 API 를 실제로
 * 확인하려면 무언가 카탈로그를 채워야 한다.
 *
 * **리포지토리를 직접 부르지 않고 유스케이스를 부른다.** 직접 넣으면 도메인 규칙을 우회한
 * 데이터가 들어가고, 시드로 만든 상품만 조회에서 이상하게 동작하는 일이 생긴다.
 */
@Component
@ConditionalOnProperty(name = ["catalog.seed"], havingValue = "true")
class CatalogSeeder(
    private val registrationService: ProductRegistrationService,
    private val managementService: ProductManagementService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        SAMPLES.forEach { sample ->
            val product = registrationService.register(sample)
            // 등록은 DRAFT 로 끝난다. 카탈로그에 보이려면 판매를 시작해야 한다.
            managementService.startSelling(requireNotNull(product.id))
        }
        log.info("카탈로그 시드를 넣었다. count={}", SAMPLES.size)
    }

    private companion object {
        val SAMPLES =
            listOf(
                RegisterProductCommand("옥스퍼드 셔츠", "면 100% 옥스퍼드 원단.", 39_000, ProductCategory.FASHION, 40),
                RegisterProductCommand("워시드 데님 팬츠", "세탁 가공한 데님.", 59_000, ProductCategory.FASHION, 12),
                RegisterProductCommand("수분 크림", "건성 피부용 보습 크림.", 28_000, ProductCategory.BEAUTY, 0),
                RegisterProductCommand("원두 1kg", "중배전 블렌드.", 24_500, ProductCategory.FOOD, 120),
                RegisterProductCommand("무선 마우스", "저소음 스위치.", 32_000, ProductCategory.DIGITAL, 8),
                RegisterProductCommand("러닝 양말 3족", "발목 길이.", 12_000, ProductCategory.SPORTS, 200),
                RegisterProductCommand("리넨 커튼", "자연 소재 암막 커튼.", 78_000, ProductCategory.LIVING, 5),
                RegisterProductCommand("에세이 선집", "국내 작가 10인.", 16_800, ProductCategory.BOOKS, 33),
            )
    }
}
