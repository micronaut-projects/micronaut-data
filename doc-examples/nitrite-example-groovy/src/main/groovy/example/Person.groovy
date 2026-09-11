package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::person[]
@MappedEntity
class Person {
    @Id
    @GeneratedValue
    String id
    String name
    int age
    List<String> interests

    Person() {}

    Person(String name, int age) {
        this.name = name
        this.age = age
    }
}
// end::person[]
