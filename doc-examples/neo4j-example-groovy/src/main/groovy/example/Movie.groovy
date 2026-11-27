package example

// tag::movie[]
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

@Node // <1>
class Movie {

    @Id // <2>
    String title

    @MappedProperty("tagline") // <3>
    String description

    Integer released

    @Relationship(type = "ACTED_IN", direction = Direction.INCOMING) // <4>
    List<Role> roles = []

    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    List<Person> directors = []
}
// end::movie[]
