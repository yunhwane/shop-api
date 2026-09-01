package com.example.shopapi.core.domain.product

import com.example.shopapi.core.domain.common.InvalidValueException
import com.example.shopapi.core.domain.common.Money
import com.example.shopapi.core.enums.ProductCategory
import com.example.shopapi.core.enums.ProductSort

/**
 * 목록에서 "여기 다음부터"를 가리키는 위치(ADR 0015).
 *
 * 정렬 키만으로는 부족해서 [id] 를 함께 담는다. 가격이 같은 상품이 여럿이면 경계에서
 * 동점 항목이 통째로 빠지거나 반복되기 때문이다. [id] 가 전순서를 만든다.
 *
 * [sort] 를 담는 이유는 다른 정렬로 이어 붙이는 것을 막기 위해서다. 커서 안의 값은
 * 특정 컬럼 기준의 위치라, 정렬이 바뀌면 아무 의미가 없다.
 */
class ProductCursor private constructor(
    val sort: ProductSort,
    val price: Money?,
    val id: Long,
) {
    companion object {
        fun of(
            sort: ProductSort,
            price: Money?,
            id: Long,
        ): ProductCursor {
            if (sort.ordersByPrice != (price != null)) {
                throw InvalidValueException("cursor", "커서 형식이 올바르지 않습니다.")
            }
            return ProductCursor(sort, price, id)
        }

        internal fun from(
            sort: ProductSort,
            product: Product,
        ): ProductCursor =
            of(
                sort = sort,
                price = if (sort.ordersByPrice) product.price else null,
                id = requireNotNull(product.id) { "저장된 상품이어야 한다" },
            )
    }
}

/**
 * 목록 조회 조건.
 *
 * [size] 에 상한을 두는 이유는, 열어 두면 한 요청으로 카탈로그 전체를 긁을 수 있어
 * 페이지네이션이 막으려던 비용이 그대로 발생하기 때문이다.
 */
class ProductSearchCriteria private constructor(
    val category: ProductCategory?,
    val keyword: String?,
    val sort: ProductSort,
    val cursor: ProductCursor?,
    val size: Int,
) {
    companion object {
        const val MIN_SIZE = 1
        const val MAX_SIZE = 100
        const val DEFAULT_SIZE = 20

        fun of(
            category: ProductCategory? = null,
            keyword: String? = null,
            sort: ProductSort = ProductSort.LATEST,
            cursor: ProductCursor? = null,
            size: Int = DEFAULT_SIZE,
        ): ProductSearchCriteria {
            if (size !in MIN_SIZE..MAX_SIZE) {
                throw InvalidValueException("size", "$MIN_SIZE~$MAX_SIZE 사이여야 합니다.")
            }
            // 조용히 다른 정렬의 결과를 돌려주는 대신 거절한다. 커서 안의 값은
            // 그 정렬 기준의 위치라서, 어긋난 채로는 어떤 답도 맞지 않는다.
            if (cursor != null && cursor.sort != sort) {
                throw InvalidValueException("cursor", "정렬 조건과 커서가 어긋납니다.")
            }
            return ProductSearchCriteria(
                category = category,
                keyword = keyword?.trim()?.ifEmpty { null },
                sort = sort,
                cursor = cursor,
                size = size,
            )
        }
    }
}

/**
 * 목록 한 쪽.
 *
 * 총 개수를 담지 않는다. `COUNT(*)` 가 매 요청마다 조건에 맞는 행을 전부 세면
 * 커서로 얻은 이점을 그대로 깎아먹는다(ADR 0015).
 */
class ProductPage(
    val items: List<Product>,
    val nextCursor: ProductCursor?,
) {
    val hasNext: Boolean
        get() = nextCursor != null

    companion object {
        /**
         * 한 개를 더 읽어 온 결과에서 페이지를 만든다.
         *
         * 다음 쪽이 있는지 아는 방법은 두 가지뿐이다 - 전체를 세거나, 한 개를 더 읽거나.
         * 세지 않기로 했으므로 더 읽는다.
         */
        fun of(
            fetched: List<Product>,
            sort: ProductSort,
            size: Int,
        ): ProductPage {
            val items = fetched.take(size)
            val nextCursor =
                if (fetched.size > size) {
                    ProductCursor.from(sort, items.last())
                } else {
                    null
                }
            return ProductPage(items, nextCursor)
        }
    }
}
