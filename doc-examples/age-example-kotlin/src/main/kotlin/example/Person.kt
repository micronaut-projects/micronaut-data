package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node

// tag::person[]
@Node // <1>
data class Person(
    @field:Id // <2>
    val name: String,
    val born: Int?
)
// end::person[]
