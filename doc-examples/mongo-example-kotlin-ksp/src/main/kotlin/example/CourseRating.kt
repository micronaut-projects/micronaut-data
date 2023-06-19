package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
data class CourseRating(
        @field:Id @GeneratedValue @Nullable
        val id: ObjectId?,
        @Relation(Relation.Kind.MANY_TO_ONE)
        val student: Student,
        @Relation(Relation.Kind.MANY_TO_ONE)
        val course: Course,
        val rating: Int
) {
    constructor(student: Student, course: Course, rating: Int) : this(null, student, course, rating)
}