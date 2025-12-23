package example

import io.micronaut.data.model.vector.Vector
import jakarta.inject.Singleton

@Singleton
class DocumentEmbeddingService(
    private val repository: DocumentEmbeddingRepository
) {
    fun saveOne(id: Long) {
        // tag::create_vector[]
        val vec: Vector = Vector.of(0.1, 0.2, 0.3)
        repository.insertEmbedding(id, vec)
        // end::create_vector[]
    }
}
