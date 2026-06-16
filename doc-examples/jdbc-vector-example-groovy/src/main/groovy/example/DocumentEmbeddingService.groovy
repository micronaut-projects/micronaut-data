package example

import io.micronaut.data.model.vector.Vector
import jakarta.inject.Singleton

@Singleton
class DocumentEmbeddingService {

    private final DocumentEmbeddingRepository repository

    DocumentEmbeddingService(DocumentEmbeddingRepository repository) {
        this.repository = repository
    }

    void saveOne() {
        // tag::create_vector[]
        Vector vec = Vector.of(0.1d, 0.2d, 0.3d)
        repository.save(vec)
        // end::create_vector[]
    }
}
