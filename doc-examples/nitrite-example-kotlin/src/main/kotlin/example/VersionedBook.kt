package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version

// tag::versioned-book[]
@MappedEntity
class VersionedBook {
    @Id
    @GeneratedValue
    var id: String? = null

    var title: String = ""

    @Version
    var version: Long? = null // <1>

    constructor()

    constructor(title: String) {
        this.title = title
    }
}
// end::versioned-book[]
