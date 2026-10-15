package example

// tag::role[]
import io.micronaut.data.graph.annotation.RelationshipId
import io.micronaut.data.graph.annotation.RelationshipProperties
import io.micronaut.data.graph.annotation.TargetNode

@RelationshipProperties // <1>
data class Role(
    @field:RelationshipId // <2>
    var id: Long? = null,
    val roles: List<String>, // <3>
    @TargetNode // <4>
    val person: Person
)
// end::role[]
