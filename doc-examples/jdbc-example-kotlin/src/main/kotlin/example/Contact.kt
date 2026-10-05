package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.time.LocalDateTime

@MappedEntity(value = "TBL_CONTACT", alias = "c")
data class Contact(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long?,
    val name: String,
    val age: Int,
    val active: Boolean?,
    val startDateTime: LocalDateTime?
)
