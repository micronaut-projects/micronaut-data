package example;

import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.ScoringFunction;
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
        repository.save(1L, vec);
        // end::create_vector[]
        DocumentEmbedding reloaded = repository.findById(1L).orElse(null);
        assertNotNull(reloaded);
        assertEquals(1L, reloaded.id());
        assertEquals(Double.TYPE, reloaded.embedding().getType());
        assertArrayEquals(new double[]{0.1d, 0.2d, 0.3d}, reloaded.embedding().toDoubleArray(), 1e-6f);

        repository.save(2L, Vector.of(0.15d, 0.2d, 0.25d));
        repository.save(3L, Vector.of(0.9d, 0.1d, 0.1d));

        var near = repository.findTop2ByEmbeddingNear(vec, 2d);
        assertEquals(2, near.size());
        assertTrue(near.stream().anyMatch(it -> it.id().equals(1L)));

        var scored = repository.searchByEmbeddingNear(vec, new Score(2d), ScoringFunction.COSINE);
        assertFalse(scored.results().isEmpty());
    }
}
