package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class Student(
        @field:Id @GeneratedValue
        val id: Long?,
        val name: String,
        @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = [Relation.Cascade.PERSIST])
        val courses: List<Course>,
        @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
        val ratings: List<CourseRating>
) {
    constructor(name: String, items: List<Course>) : this(null, name, items, emptyList())
}
