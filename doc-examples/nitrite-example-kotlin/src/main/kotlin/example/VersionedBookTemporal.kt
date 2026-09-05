package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import java.time.Instant

// tag::versioned-book-temporal[]
@MappedEntity
class VersionedBookTemporal {
    @Id
    @GeneratedValue
    var id: String? = null

    var title: String = ""

    @Version
    var version: Instant? = null // <1>

    constructor(title: String) {
        this.title = title
    }
    // end::versioned-book-temporal[]

    constructor()
// tag::versioned-book-temporal[]
}
// end::versioned-book-temporal[]
