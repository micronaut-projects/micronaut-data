package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonView
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.JoinColumn

// tag::class-example[]
@JsonView(entity = Student::class)
data class StudentView(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long?,
    val name: String,
    @field:JoinColumn(name = "id", referencedColumnName = "student_id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    val schedule: List<StudentScheduleSubView>
)
// end::class-example[]
