package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node

@Node
data class Person(
    @field:Id
    val name: String,
    val born: Int
)
