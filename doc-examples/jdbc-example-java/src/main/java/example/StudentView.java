package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import java.util.List;

// tag::class-example[]
@JsonView(entity = Student.class)
public class StudentView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    private String name;

    @JoinColumn(name = "id", referencedColumnName = "student_id")
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<StudentScheduleSubView> schedule;
}
// end::class-example[]
