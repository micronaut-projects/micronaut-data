package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonSubView
import io.micronaut.data.annotation.JsonView

// tag::class-example[]
@JsonSubView(entity = StudentClass::class, operations = [JsonView.Operation.UPDATE, JsonView.Operation.INSERT])
data class StudentScheduleSubView(
    @field:Id
    val id: Long?
)
// end::class-example[]
