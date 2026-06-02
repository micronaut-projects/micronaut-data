package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

// tag::student[]
@MappedEntity
class Student {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "students")
    Set<Book> books = [] as HashSet // <1>

    Student() {}

    Student(String name) {
        this.name = name
    }
}
// end::student[]
