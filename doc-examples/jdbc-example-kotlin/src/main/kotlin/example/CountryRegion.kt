package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.naming.NamingStrategies

// tag::namingStrategy[]
@MappedEntity(namingStrategy = NamingStrategies.Raw::class)
class CountryRegion(
// end::namingStrategy[]
    val name: String,
    @Relation(Relation.Kind.MANY_TO_ONE)
    val country: Country?
) {
    @GeneratedValue
    @Id
    var id: Long? = null
// tag::namingStrategy[]
    // ...
}
// end::namingStrategy[]
