package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;

// tag::class-example[]
@JsonSubView(entity = StudentClass.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
public class StudentScheduleSubView {
// end::class-example[]
    @Id
    private Long id;
}
