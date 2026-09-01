package com.example.shopapi.storage.support

import org.springframework.dao.DataIntegrityViolationException

/**
 * 유니크 제약 위반을 이름으로 식별한다.
 *
 * 드라이버마다 예외 메시지가 다르므로, 제약에 이름을 명시적으로 붙이고(`uk_...`)
 * 그 이름이 메시지에 들어 있는지로 판별한다. 메시지 파싱은 취약하지만, JDBC 표준이
 * "어느 제약이 깨졌는가"를 구조화해서 알려주지 않으므로 다른 방법이 없다.
 *
 * 실 DB 로 옮길 때 이 판별에 대한 테스트가 반드시 필요하다.
 */
internal fun DataIntegrityViolationException.violates(constraintName: String): Boolean =
    mostSpecificCause.message.orEmpty().contains(constraintName, ignoreCase = true)
