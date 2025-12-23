package example;

import io.micronaut.data.model.vector.Vector;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class DocumentEmbeddingRepositorySpec {

    @Inject
    DocumentEmbeddingRepository repository;

    @Test
    void saveAndFetchEmbedding() {
        repository.deleteAll();
        // tag::create_vector[]
        Vector vec = Vector.of(0.1d, 0.2d, 0.3d);
        repository.insertEmbedding(1L, vec);
        // end::create_vector[]
        DocumentEmbedding reloaded = repository.findById(1L).orElse(null);
        assertNotNull(reloaded);
        assertEquals(1L, reloaded.id());
        assertEquals(Double.TYPE, reloaded.embedding().getType());
        assertArrayEquals(new double[]{0.1d, 0.2d, 0.3d}, reloaded.embedding().toDoubleArray(), 1e-6f);
    }
}
