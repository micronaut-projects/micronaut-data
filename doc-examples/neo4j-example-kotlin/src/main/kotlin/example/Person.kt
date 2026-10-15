package example

// tag::person[]
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship
import java.util.UUID

@Node
data class Person(
    @field:Id
    @field:AutoPopulated // <1>
    var id: UUID? = null,
    val name: String,
    val born: Int?,
    @Relationship("FOLLOWS") // <2>
    var follows: List<Person> = emptyList()
)
// end::person[]
