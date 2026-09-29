package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonSubView
import io.micronaut.data.annotation.MappedProperty

@Embeddable
// tag::class-example[]
@JsonSubView(entity = Teacher::class)
data class TeacherSubView(
    @field:Id
    @field:MappedProperty("id")
    val teachID: Long?,
    @field:MappedProperty("name")
    val teacher: String
)
// end::class-example[]
