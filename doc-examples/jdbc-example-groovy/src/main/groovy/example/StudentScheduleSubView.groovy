package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonSubView
import io.micronaut.data.annotation.JsonView

// tag::class-example[]
@JsonSubView(entity = StudentClass, operations = [JsonView.Operation.UPDATE, JsonView.Operation.INSERT])
class StudentScheduleSubView {
    @Id
    Long id
}
// end::class-example[]
