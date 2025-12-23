package example;

import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

@Singleton
public class DocumentEmbeddingService {

    private final DocumentEmbeddingRepository repository;

    public DocumentEmbeddingService(DocumentEmbeddingRepository repository) {
        this.repository = repository;
    }

    public void saveOne(long id) {
        // tag::create_vector[]
        Vector vec = Vector.of(0.1d, 0.2d, 0.3d);
        repository.insertEmbedding(id, vec);
        // end::create_vector[]
    }
}
