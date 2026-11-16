package example

import io.micronaut.data.graph.annotation.RelationshipId
import io.micronaut.data.graph.annotation.RelationshipProperties
import io.micronaut.data.graph.annotation.TargetNode
import java.math.BigDecimal

// tag::transfer[]
@RelationshipProperties
data class Transfer(
    @field:RelationshipId
    val id: Long?,
    val amount: BigDecimal,
    @field:TargetNode
    val to: Account?
)
// end::transfer[]
