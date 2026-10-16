package example

import io.micronaut.data.graph.annotation.RelationshipId
import io.micronaut.data.graph.annotation.RelationshipProperties
import io.micronaut.data.graph.annotation.TargetNode

// tag::transfer[]
@RelationshipProperties
class Transfer {
    @RelationshipId
    Long id
    BigDecimal amount
    @TargetNode
    Account to
}
// end::transfer[]
