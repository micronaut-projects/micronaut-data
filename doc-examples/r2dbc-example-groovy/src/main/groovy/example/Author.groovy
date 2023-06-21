package example

import io.micronaut.data.annotation.*
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@MappedEntity
class Author {
    @GeneratedValue
    @Id
    Long id
    final String name

    Author(String name) {
        this.name = name
    }
}

