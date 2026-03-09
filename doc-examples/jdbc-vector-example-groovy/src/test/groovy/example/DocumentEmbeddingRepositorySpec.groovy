package example

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class DocumentEmbeddingRepositorySpec extends Specification {

    @Inject
    DocumentEmbeddingRepository repository

    void "save and fetch embedding"() {
        when:
        repository.deleteAll()
        Vector vec = Vector.of(0.1d, 0.2d, 0.3d)
        repository.save(1L, vec)
        repository.save(2L, Vector.of(0.15d, 0.2d, 0.25d))
        repository.save(3L, Vector.of(0.9d, 0.1d, 0.1d))
        def reloaded = repository.findById(1L).orElse(null)
        def near = repository.findTop2ByEmbeddingNear(vec, 2d)
        def scored = repository.searchByEmbeddingNear(vec, new Score(2d), ScoringFunction.COSINE)

        then:
        reloaded != null
        reloaded.id == 1L
        reloaded.embedding.type == Double.TYPE
        reloaded.embedding.toDoubleArray() == [0.1d, 0.2d, 0.3d] as double[]
        near.size() == 2
        near*.id.contains(1L)
        !scored.results().isEmpty()
    }
}
