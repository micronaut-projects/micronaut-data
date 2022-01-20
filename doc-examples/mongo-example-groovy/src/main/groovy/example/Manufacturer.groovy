
package example

import io.micronaut.core.annotation.Creator
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Manufacturer {
    @Id
    @GeneratedValue
    Long id
    final String name

    @Creator
    Manufacturer(String name) {
        this.name = name
    }
}
