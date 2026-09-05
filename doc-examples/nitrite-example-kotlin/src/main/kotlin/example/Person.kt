package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::person[]
@MappedEntity
class Person {
    @Id
    @GeneratedValue
    var id: String? = null

    var name: String = ""

    var age: Int = 0

    var interests: MutableList<String>? = null

    constructor()

    constructor(name: String, age: Int) {
        this.name = name
        this.age = age
    }
}
// end::person[]
