package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    String id

    String title

    Book() {
    }

    Book(String title) {
        this.title = title
    }
}
// end::book[]

