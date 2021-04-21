package example

import edu.umd.cs.findbugs.annotations.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class CourseRating(
        @field:Id @GeneratedValue @Nullable
        val id: Long?,
        @Relation(Relation.Kind.MANY_TO_ONE)
        val student: Student,
        @Relation(Relation.Kind.MANY_TO_ONE)
        val course: Course,
        val rating: Int
) {
    constructor(student: Student, course: Course, rating: Int) : this(null, student, course, rating)
}