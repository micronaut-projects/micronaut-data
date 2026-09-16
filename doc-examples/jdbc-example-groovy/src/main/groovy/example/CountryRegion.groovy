package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.naming.NamingStrategies

// tag::namingStrategy[]
@MappedEntity(namingStrategy = NamingStrategies.Raw)
class CountryRegion {
// end::namingStrategy[]

    @GeneratedValue
    @Id
    Long id

    final String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    final Country country

    CountryRegion(String name, @Nullable Country country) {
        this.name = name
        this.country = country
    }
// tag::namingStrategy[]
    // ...
}
// end::namingStrategy[]
