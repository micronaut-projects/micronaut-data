package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Sharded

// tag::root[]
@Sharded(tablespaceSet = "TS_SUBSCRIBERS") // <1>
@MappedEntity
data class Subscriber(
    @field:Id
    val subscriberId: String, // <2>
    val name: String
)
// end::root[]
