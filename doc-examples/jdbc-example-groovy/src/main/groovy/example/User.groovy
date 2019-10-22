package example

import groovy.transform.EqualsAndHashCode
import io.micronaut.data.annotation.*

@MappedEntity
@Where("enabled = true") // <1>
@EqualsAndHashCode(includes = "name")
class User {
    @GeneratedValue
    @Id
    Long id
    String name
    boolean enabled = true // <2>

    User(String name) {
        this.name = name
    }
}
