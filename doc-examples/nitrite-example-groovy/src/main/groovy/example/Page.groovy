package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::page[]
@MappedEntity
class Page {
    @Id
    @GeneratedValue
    String id

    int pageNumber

    String content

    Page() {
    }

    Page(int pageNumber, String content) {
        this.pageNumber = pageNumber
        this.content = content
    }
}
// end::page[]
