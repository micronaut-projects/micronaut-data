package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Relation

@Embeddable
data class Relationship(
    val type: RelationshipType = RelationshipType.CLIENT,
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    val status: RelationshipStatus
)
