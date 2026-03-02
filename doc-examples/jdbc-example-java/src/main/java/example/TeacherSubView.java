package example;

import io.micronaut.data.annotation.*;

@Embeddable
// tag::class-example[]
@JsonSubView(entity = Teacher.class)
public class TeacherSubView {
    @Id
    @MappedProperty(value = "id")
    private Long teachID;

    @MappedProperty(value = "name")
    private String teacher;
}
// end::class-example[]
