package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Sharded

// tag::root[]
@Sharded(tablespaceSet = "TS_SUBSCRIBERS") // <1>
@MappedEntity
class Subscriber {
    @Id
    String subscriberId // <2>
    String name
}
// end::root[]
