package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

// tag::movie[]
@Node
class Movie {
    @Id
    String title
    Integer released
    @Relationship(type = "DIRECTED", direction = Direction.INCOMING) // <1>
    List<Person> directors = []
}
// end::movie[]
