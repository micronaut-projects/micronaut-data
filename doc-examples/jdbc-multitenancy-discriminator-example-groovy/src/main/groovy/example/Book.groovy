package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TenantId

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    Long id
    String title
    int pages
    @TenantId
    String tenant
// end::book[]

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }

// tag::book[]
// ...
}
// end::book[]
