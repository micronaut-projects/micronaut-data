package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Person {
    @Id
    @GeneratedValue
    String id
    String name
    int age
    List<String> interests
}

