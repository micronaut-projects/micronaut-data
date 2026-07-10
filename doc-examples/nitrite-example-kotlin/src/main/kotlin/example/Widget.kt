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

    constructor()

    constructor(name: String) {
        this.name = name
    }
}
// end::uuid-widget[]
