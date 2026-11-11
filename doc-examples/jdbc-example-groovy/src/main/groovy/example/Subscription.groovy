package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Sharded

// tag::child[]
@Sharded(parent = Subscriber, tablespaceSet = "TS_SUBSCRIBERS") // <1>
@MappedEntity
class Subscription {
    @EmbeddedId
    SubscriptionId id // <2>
    String plan
}
// end::child[]
