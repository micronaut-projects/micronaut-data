package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Book {
    @Id
    @GeneratedValue
    Long id
    String title
    int pages

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }
}
