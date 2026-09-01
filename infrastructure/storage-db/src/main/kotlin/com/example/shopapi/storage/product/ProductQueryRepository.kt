package com.example.shopapi.storage.product

import com.example.shopapi.core.domain.product.ProductSearchCriteria
import com.example.shopapi.core.enums.ProductSort
import com.example.shopapi.core.enums.ProductStatus
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

/**
 * 커서 목록 조회. 조건이 요청마다 달라 JPQL 을 그때그때 조립한다.
 *
 * 파생 쿼리로는 표현할 수 없다. 커서 조건이 `(가격, id)` 두 컬럼에 걸친 사전식 비교이고,
 * 정렬 기준마다 부등호 방향이 다르기 때문이다.
 */
@Repository
internal class ProductQueryRepository(
    private val entityManager: EntityManager,
) {
    /** 다음 쪽이 있는지 알기 위해 요청한 것보다 한 개를 더 읽어 돌려준다(ADR 0015) */
    fun findOnSalePage(criteria: ProductSearchCriteria): List<ProductJpaEntity> {
        val conditions = mutableListOf("p.status = :status")
        val parameters = mutableMapOf<String, Any>("status" to ProductStatus.ON_SALE)

        criteria.category?.let {
            conditions += "p.category = :category"
            parameters["category"] = it
        }
        criteria.keyword?.let {
            conditions += "lower(p.name) like :keyword escape '$LIKE_ESCAPE'"
            parameters["keyword"] = "%${escapeLike(it.lowercase())}%"
        }
        criteria.cursor?.let { cursor ->
            conditions += cursorCondition(criteria.sort)
            parameters["cursorId"] = cursor.id
            cursor.price?.let { parameters["cursorPrice"] = it.amount }
        }

        val jpql =
            "select p from ProductJpaEntity p " +
                "where ${conditions.joinToString(" and ")} " +
                "order by ${orderBy(criteria.sort)}"

        val query = entityManager.createQuery(jpql, ProductJpaEntity::class.java)
        parameters.forEach { (name, value) -> query.setParameter(name, value) }
        return query.setMaxResults(criteria.size + 1).resultList
    }

    /**
     * 커서 위치 **다음**부터를 고른다.
     *
     * 정렬 키만 비교하면 가격이 같은 상품들이 경계에서 통째로 빠지거나 반복된다.
     * `id` 를 마지막 비교 대상으로 붙여 전순서를 만든다. 부등호 방향은 `order by` 의
     * 타이브레이커와 반드시 같아야 한다.
     *
     * `PRICE_DESC` 의 타이브레이커가 `id desc` 인 이유는 인덱스 때문이다. 인덱스가
     * `(status, price, id)` 오름차순이라 역방향 스캔이 내주는 순서는 `price desc, id desc`
     * 다. 여기서 `id asc` 를 고르면 DB 가 전부 읽어 다시 정렬해야 해서, 커서로 얻으려던
     * "어느 위치든 인덱스 탐색 한 번"이 사라진다(ADR 0015).
     */
    private fun cursorCondition(sort: ProductSort): String =
        when (sort) {
            ProductSort.LATEST -> "p.id < :cursorId"
            ProductSort.PRICE_ASC -> "(p.price > :cursorPrice or (p.price = :cursorPrice and p.id > :cursorId))"
            ProductSort.PRICE_DESC -> "(p.price < :cursorPrice or (p.price = :cursorPrice and p.id < :cursorId))"
        }

    private fun orderBy(sort: ProductSort): String =
        when (sort) {
            ProductSort.LATEST -> "p.id desc"
            ProductSort.PRICE_ASC -> "p.price asc, p.id asc"
            ProductSort.PRICE_DESC -> "p.price desc, p.id desc"
        }

    private companion object {
        const val LIKE_ESCAPE = '\\'

        /**
         * `%` 와 `_` 를 리터럴로 만든다.
         *
         * 이스케이프하지 않으면 "50%" 를 찾는 사용자가 와일드카드를 보낸 셈이 되어
         * 엉뚱한 결과를 받는다. 검색어가 `%` 하나면 전체 스캔이 된다.
         */
        fun escapeLike(raw: String): String =
            raw
                .replace(LIKE_ESCAPE.toString(), "$LIKE_ESCAPE$LIKE_ESCAPE")
                .replace("%", "$LIKE_ESCAPE%")
                .replace("_", "${LIKE_ESCAPE}_")
    }
}
