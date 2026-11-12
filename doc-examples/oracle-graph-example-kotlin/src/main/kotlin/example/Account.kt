package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

// tag::account[]
@Node(value = "accounts", labels = ["Account"]) // <1>
data class Account(
    @field:Id // <2>
    val id: Long,
    val name: String,
    @Relationship("TRANSFERS") // <3>
    val transfers: List<Transfer> = emptyList()
)
// end::account[]
