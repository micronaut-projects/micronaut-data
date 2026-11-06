package example

// tag::person[]
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Relation
import io.micronaut.data.graph.annotation.Labels
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
    var follows: List<Person> = emptyList(),
    // end::person[]
    // tag::cascade[]
    @Relationship(value = "KNOWS", cascade = Relation.Cascade.NONE) // <1>
    var knows: List<Person> = emptyList(),
    // end::cascade[]
    // tag::labels[]
    @field:Labels // <1>
    var labels: Set<String> = emptySet()
    // end::labels[]
// tag::person[]
)
// end::person[]
