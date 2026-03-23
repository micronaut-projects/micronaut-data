package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import java.util.HashSet

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    var id: String? = null

    var title: String = ""

    // tag::book-many-to-one[]
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    var author: Author? = null // <1>
    // end::book-many-to-one[]

    // tag::book-many-to-many[]
    @Relation(value = Relation.Kind.MANY_TO_MANY)
    var students: MutableSet<Student> = HashSet() // <1>
    // end::book-many-to-many[]

    constructor()

    constructor(title: String) {
        this.title = title
    }
}
// end::book[]
