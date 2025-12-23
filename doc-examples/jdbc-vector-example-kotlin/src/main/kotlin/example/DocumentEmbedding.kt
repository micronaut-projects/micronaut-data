package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column

@MappedEntity("document_embedding")
data class DocumentEmbedding(
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,

    @field:Column(length = 3)
    val embedding: Vector
)
