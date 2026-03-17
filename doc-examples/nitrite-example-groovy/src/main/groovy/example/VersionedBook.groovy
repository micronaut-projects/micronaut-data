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
    String id

    String title

    @Version
    Long version // <1>

    VersionedBook() {}

    VersionedBook(String title) {
        this.title = title
    }
}
// end::versioned-book[]
