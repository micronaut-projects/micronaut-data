package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonView
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.JoinColumn

// tag::class-example[]
@JsonView(entity = Student)
class StudentView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id

    String name

    @JoinColumn(name = "id", referencedColumnName = "student_id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    List<StudentScheduleSubView> schedule
}
// end::class-example[]
