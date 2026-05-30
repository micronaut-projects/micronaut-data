package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
data class Person(
    @field:Id
    @field:GeneratedValue
    var id: String? = null,
    var name: String,
    var age: Int,
    var interests: List<String>? = null
)

