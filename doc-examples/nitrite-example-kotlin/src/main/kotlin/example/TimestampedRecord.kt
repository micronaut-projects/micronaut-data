package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::lifecycle-entity[]
@MappedEntity
class TimestampedRecord {
    @Id
    @GeneratedValue
    var id: String? = null

    var name: String? = null

    constructor()

    constructor(name: String) {
        this.name = name
    }
}
// end::lifecycle-entity[]
