package example

import io.micronaut.data.annotation.Duplicated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::duplicated[]
@Duplicated(tablespace = "PLANS_TSP")
@MappedEntity
class Plan {
    @Id
    String code
    String name
}
// end::duplicated[]
