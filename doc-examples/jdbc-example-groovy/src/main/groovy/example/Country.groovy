package example

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

// tag::country[]
@MappedEntity
class Country {

    @Id
    @AutoPopulated
    UUID uuid

    final String name

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "country")
    Set<CountryRegion> regions

    Country(String name) {
        this.name = name
    }
}
// end::country[]
