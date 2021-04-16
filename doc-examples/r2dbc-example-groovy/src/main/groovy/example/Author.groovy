package example

import io.micronaut.data.annotation.*

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

