package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import java.util.HashSet

// tag::student[]
@MappedEntity
class Student {
    @Id
    @GeneratedValue
    var id: String? = null

    var name: String = ""

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "students")
    var books: MutableSet<Book> = HashSet() // <1>

    constructor()

    constructor(name: String) {
        this.name = name
    }
}
// end::student[]
