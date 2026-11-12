package example

import io.micronaut.data.annotation.Embeddable

@Embeddable
data class SubscriptionId(val subscriberId: String, val subscriptionNo: Long)
