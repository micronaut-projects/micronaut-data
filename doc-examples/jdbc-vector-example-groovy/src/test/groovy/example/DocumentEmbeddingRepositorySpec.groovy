package example

import io.micronaut.data.model.vector.Vector
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
        Vector vec = Vector.of(0.1f, 0.2f, 0.3f)
        repository.insertEmbedding(1L, vec)
        def reloaded = repository.findById(1L).orElse(null)

        then:
        reloaded != null
        reloaded.id == 1L
        reloaded.embedding.type == Double.TYPE
        reloaded.embedding.toFloatArray() == [0.1f, 0.2f, 0.3f] as float[]
    }
}
