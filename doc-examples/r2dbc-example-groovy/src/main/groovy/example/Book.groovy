package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    Long id
    final String title
    final int pages
    @Relation(Relation.Kind.MANY_TO_ONE)
    final Author author

    Book(String title, int pages, Author author) {
        this.title = title
        this.pages = pages
        this.author = author
    }
}
