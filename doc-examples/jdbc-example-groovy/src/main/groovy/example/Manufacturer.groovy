
package example

import io.micronaut.core.annotation.Creator

import jakarta.persistence.*

@Entity
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
