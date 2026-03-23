package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

// tag::author[]
@MappedEntity
class Author {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = 'author', cascade = [Relation.Cascade.PERSIST])
    Set<Book> books = [] as Set // <1>

    Author() {
    }

    Author(String name) {
        this.name = name
    }

    void addBook(Book book) {
        books << book
        book.author = this
    }
}
// end::author[]
