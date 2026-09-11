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
    // end::author[]

    Author() {
    }

    // tag::author[]
    Author(String name) {
        this.name = name
    }
    // end::author[]

    void addBook(Book book) {
        books << book
        book.author = this
    }
// tag::author[]
}
// end::author[]
