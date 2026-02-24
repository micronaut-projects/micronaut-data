package example

import io.micronaut.data.annotation.*
import java.time.Instant

@MappedEntity
data class Client(
    @field:Id @GeneratedValue val id: Long? = null,
    val name: String,
    @Relation(value = Relation.Kind.EMBEDDED)
    @MappedProperty(value = "relationship")
    val relationship: Relationship,

    @DateCreated
    val createdAt: Instant = Instant.now(),
    @DateUpdated
    val updatedAt: Instant = Instant.now()
)
