package example

import io.micronaut.data.annotation.Duplicated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::duplicated[]
@Duplicated(tablespace = "PLANS_TSP")
@MappedEntity
data class Plan(@field:Id val code: String, val name: String)
// end::duplicated[]
