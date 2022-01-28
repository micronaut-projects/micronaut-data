package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import org.bson.types.ObjectId

@MappedEntity
class Person {
    @field:Id
    @GeneratedValue
    var id: ObjectId? = null
    var name: String? = null
    var age = 0

    constructor(name: String?, age: Int) : this(null, name, age) {}
    constructor(id: ObjectId?, name: String?, age: Int) {
        this.id = id
        this.name = name
        this.age = age
    }
}