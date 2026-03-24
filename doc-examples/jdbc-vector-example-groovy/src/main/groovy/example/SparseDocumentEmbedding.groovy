package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.vector.Vector

@MappedEntity("sparse_document_embedding")
class SparseDocumentEmbedding {

    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 5, shape = VectorShape.SPARSE)
    Vector embedding
}
