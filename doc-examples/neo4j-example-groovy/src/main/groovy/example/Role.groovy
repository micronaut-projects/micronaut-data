package example

// tag::role[]
import io.micronaut.data.graph.annotation.RelationshipId
import io.micronaut.data.graph.annotation.RelationshipProperties
import io.micronaut.data.graph.annotation.TargetNode

@RelationshipProperties // <1>
class Role {

    @RelationshipId // <2>
    Long id

    List<String> roles // <3>

    @TargetNode // <4>
    Person person
}
// end::role[]
