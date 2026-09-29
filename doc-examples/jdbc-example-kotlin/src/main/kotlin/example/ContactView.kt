package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonView
import java.time.LocalDateTime

// tag::record-example[]
@JsonView(value = "CONTACT_VIEW", alias = "cv", entity = Contact::class)
data class ContactView(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long?,
    val name: String,
    val age: Int,
    val startDateTime: LocalDateTime,
    val active: Boolean
)
// end::record-example[]
