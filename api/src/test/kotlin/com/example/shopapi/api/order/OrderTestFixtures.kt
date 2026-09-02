package com.example.shopapi.api.order

import com.example.shopapi.api.order.application.ShippingAddressCommand

/**
 * 배송지가 주문의 필수 입력이 되면서(ADR 0020) 주문을 만드는 테스트가 전부 같은 값을
 * 필요로 하게 됐다. 배송지 자체가 관심사가 아닌 테스트는 이 기본값을 쓴다.
 */
fun shippingAddressCommand(
    recipientName: String = "전윤환",
    phone: String = "010-1234-5678",
    postalCode: String = "04524",
    addressLine1: String = "서울 중구 세종대로 110",
    addressLine2: String? = "5층",
): ShippingAddressCommand =
    ShippingAddressCommand(
        recipientName = recipientName,
        phone = phone,
        postalCode = postalCode,
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
    )

/** HTTP 로 주문을 넣는 테스트가 쓰는 요청 본문 조각. [shippingAddressCommand] 와 같은 값이다 */
const val SHIPPING_ADDRESS_JSON: String =
    """"shippingAddress":{"recipientName":"전윤환","phone":"010-1234-5678","postalCode":"04524",""" +
        """"addressLine1":"서울 중구 세종대로 110","addressLine2":"5층"}"""
