package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.util.UUID

// tag::uuid-widget[]
@MappedEntity("widgets")
class Widget {
    @Id
    @GeneratedValue
    var id: UUID? = null

    var name: String? = null
    // end::uuid-widget[]

    constructor()

    // tag::uuid-widget[]
    constructor(name: String) {
        this.name = name
    }
    // end::uuid-widget[]
// tag::uuid-widget[]
}
// end::uuid-widget[]
