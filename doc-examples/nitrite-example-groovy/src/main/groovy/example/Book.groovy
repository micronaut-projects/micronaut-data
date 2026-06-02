package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    String id

    String title

    // tag::book-many-to-one[]
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    Author author // <1>
    // end::book-many-to-one[]

    // tag::book-many-to-many[]
    @Relation(value = Relation.Kind.MANY_TO_MANY)
    Set<Student> students = [] as HashSet // <1>
    // end::book-many-to-many[]

    List<Page> pages = []

    Book() {
    }

    Book(String title) {
        this.title = title
    }
}
// end::book[]
