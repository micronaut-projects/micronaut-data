package example

import groovy.transform.EqualsAndHashCode
import io.micronaut.data.annotation.Embeddable

@EqualsAndHashCode
@Embeddable
class SubscriptionId {
    final String subscriberId
    final Long subscriptionNo

    SubscriptionId(String subscriberId, Long subscriptionNo) {
        this.subscriberId = subscriberId
        this.subscriptionNo = subscriptionNo
    }
}
