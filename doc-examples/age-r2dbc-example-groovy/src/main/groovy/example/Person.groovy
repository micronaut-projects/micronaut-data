package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node

// tag::person[]
@Node // <1>
class Person {
    @Id // <2>
    String name
    Integer born
}
// end::person[]
