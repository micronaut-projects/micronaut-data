package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import java.util.HashSet

// tag::author[]
@MappedEntity
class Author {
    @Id
    @GeneratedValue
    var id: String? = null

    var name: String = ""

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "author", cascade = [Relation.Cascade.PERSIST])
    var books: MutableSet<Book> = HashSet() // <1>
    // end::author[]

    constructor()

    // tag::author[]
    constructor(name: String) {
        this.name = name
    }
    // end::author[]

    fun addBook(book: Book) {
        books.add(book)
        book.author = this
    }
// tag::author[]
}
// end::author[]
