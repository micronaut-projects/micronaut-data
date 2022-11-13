package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Person {
    @field:Id
    @GeneratedValue
    var id: String? = null
    var name: String? = null
    var age = 0

    constructor(name: String?, age: Int) : this(null, name, age)
    constructor(id: String?, name: String?, age: Int) {
        this.id = id
        this.name = name
        this.age = age
    }
}
