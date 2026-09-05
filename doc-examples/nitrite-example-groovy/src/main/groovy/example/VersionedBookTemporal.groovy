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
    String id

    String title

    @Version
    Instant version // <1>

    VersionedBookTemporal(String title) {
        this.title = title
    }
    // end::versioned-book-temporal[]

    VersionedBookTemporal() {}
// tag::versioned-book-temporal[]
}
// end::versioned-book-temporal[]
