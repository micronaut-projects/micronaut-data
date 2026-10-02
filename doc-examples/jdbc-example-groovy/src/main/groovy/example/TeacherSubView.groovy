package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonSubView
import io.micronaut.data.annotation.MappedProperty

@Embeddable
// tag::class-example[]
@JsonSubView(entity = Teacher)
class TeacherSubView {
    @Id
    @MappedProperty("id")
    Long teachID

    @MappedProperty("name")
    String teacher
}
// end::class-example[]
