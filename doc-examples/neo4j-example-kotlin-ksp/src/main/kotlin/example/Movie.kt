package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

@Node
data class Movie(
    @field:Id
    val title: String,
    val released: Int,
    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    val directors: List<Person> = emptyList()
)
