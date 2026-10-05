package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = "TBL_ADDRESS", alias = "a")
class Address {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id
    String street
    String city
}
