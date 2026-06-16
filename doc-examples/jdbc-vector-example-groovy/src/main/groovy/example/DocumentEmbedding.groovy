package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column

@MappedEntity("document_embedding")
class DocumentEmbedding {

    @Id
    @GeneratedValue
    Long id

    @Column(length = 3)
    @VectorIndex(
        vectorIndexType = VectorIndexType.IVF,
        distanceType = VectorIndexType.DistanceType.COSINE,
        accuracy = 90
    )
    Vector embedding
}
