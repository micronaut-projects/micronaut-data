package example

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import java.util.UUID

// tag::country[]
@MappedEntity
class Country(val name: String) {

    @Id
    @AutoPopulated
    var uuid: UUID? = null

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "country")
    var regions: Set<CountryRegion>? = null
}
// end::country[]
