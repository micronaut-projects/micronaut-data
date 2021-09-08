package example

import io.micronaut.data.annotation.Id
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue

@Entity
class Person {
    @Id
    @GeneratedValue
    var id: Long? = null
    var name: String? = null
    var age = 0

    constructor(name: String?, age: Int) : this(null, name, age) {}
    constructor(id: Long?, name: String?, age: Int) {
        this.id = id
        this.name = name
        this.age = age
    }
}