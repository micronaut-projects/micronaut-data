package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TenantId

// tag::book[]
@MappedEntity
data class Book(
    @field:Id
    @field:GeneratedValue
    var id: Long? = null,
    val title: String,
    val pages: Int,
    @field:TenantId
    var tenant: String? = null
)
// end::book[]
