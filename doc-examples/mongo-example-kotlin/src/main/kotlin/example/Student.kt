package example

import io.micronaut.data.annotation.*
import org.bson.types.ObjectId

@MappedEntity
data class Student(
        @field:Id @GeneratedValue
        val id: ObjectId?,
        @field:Version
        val version: Long?,
        val name: String,
        @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = [Relation.Cascade.PERSIST])
        val courses: List<Course>,
        @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
        val ratings: List<CourseRating>
) {
    constructor(name: String, items: List<Course>) : this(null, null, name, items, emptyList())
}