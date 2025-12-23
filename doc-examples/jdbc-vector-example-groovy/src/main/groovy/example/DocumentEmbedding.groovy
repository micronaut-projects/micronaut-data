package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column

@MappedEntity("document_embedding")
class DocumentEmbedding {

    @Id
    @GeneratedValue
    Long id

    @Column(length = 3)
    Vector embedding
}
