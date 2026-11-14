package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable

// tag::book[]
@Serdeable
@MappedEntity(value = "book", schema = "public")
data class Book(@field:Id val id: Long, val title: String)
// end::book[]
