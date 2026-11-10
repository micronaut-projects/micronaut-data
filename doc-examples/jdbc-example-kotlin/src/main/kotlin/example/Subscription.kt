package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Sharded

// tag::child[]
@Sharded(parent = Subscriber::class, tablespaceSet = "TS_SUBSCRIBERS") // <1>
@MappedEntity
data class Subscription(
    @field:EmbeddedId
    val id: SubscriptionId, // <2>
    val plan: String
)
// end::child[]
