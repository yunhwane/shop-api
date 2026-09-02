package com.example.shopapi.storage.shipping

import com.example.shopapi.core.domain.shipping.AddressLine1
import com.example.shopapi.core.domain.shipping.AddressLine2
import com.example.shopapi.core.domain.shipping.PhoneNumber
import com.example.shopapi.core.domain.shipping.PostalCode
import com.example.shopapi.core.domain.shipping.RecipientName
import com.example.shopapi.core.domain.shipping.ShippingAddress
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 배송지의 영속성 모델. `orders` 와 `shipments` 양쪽에 같은 모양으로 박힌다(ADR 0020).
 *
 * 두 테이블에 같은 값이 두 번 저장되는 것은 의도된 것이다 - `Shipment` 는 `Order` 를
 * 다시 읽지 않고도 자기 완결적이어야 한다.
 */
@Embeddable
class ShippingAddressEmbeddable(
    @Column(name = "recipient_name", nullable = false, length = 50)
    var recipientName: String,
    @Column(name = "phone", nullable = false, length = 20)
    var phone: String,
    @Column(name = "postal_code", nullable = false, length = 5)
    var postalCode: String,
    @Column(name = "address_line1", nullable = false, length = 200)
    var addressLine1: String,
    @Column(name = "address_line2", length = 100)
    var addressLine2: String?,
) {
    fun toDomain(): ShippingAddress =
        ShippingAddress(
            recipientName = RecipientName.reconstitute(recipientName),
            phone = PhoneNumber.reconstitute(phone),
            postalCode = PostalCode.reconstitute(postalCode),
            addressLine1 = AddressLine1.reconstitute(addressLine1),
            addressLine2 = addressLine2?.let { AddressLine2.reconstitute(it) },
        )

    companion object {
        fun from(address: ShippingAddress): ShippingAddressEmbeddable =
            ShippingAddressEmbeddable(
                recipientName = address.recipientName.value,
                phone = address.phone.value,
                postalCode = address.postalCode.value,
                addressLine1 = address.addressLine1.value,
                addressLine2 = address.addressLine2?.value,
            )
    }
}
