package example

// tag::movie[]
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

@Node // <1>
data class Movie(
    @field:Id // <2>
    val title: String,
    @field:MappedProperty("tagline") // <3>
    val description: String?,
    val released: Int?,
    @Relationship(type = "ACTED_IN", direction = Direction.INCOMING) // <4>
    val roles: List<Role> = emptyList(),
    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    val directors: List<Person> = emptyList()
)
// end::movie[]
