package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

// tag::account[]
@Node("Account") // <1>
class Account {
    @Id // <2>
    Long id
    String name
    @Relationship("TRANSFERS") // <3>
    List<Transfer> transfers = []
}
// end::account[]
