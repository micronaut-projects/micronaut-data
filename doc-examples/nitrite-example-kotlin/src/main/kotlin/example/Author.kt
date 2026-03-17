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

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "author", cascade = [Relation.Cascade.PERSIST]) // <1>
    var books: MutableSet<Book> = HashSet()

    constructor()

    constructor(name: String) {
        this.name = name
    }

    fun addBook(book: Book) {
        books.add(book)
        book.author = this
    }
}
// end::author[]
