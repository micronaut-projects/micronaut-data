package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class Course(
        @field:Id @GeneratedValue
        val id: Long?,
        val name: String,
        @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
        val students: List<Student>
) {
    constructor(name: String) : this(null, name, emptyList())
}
