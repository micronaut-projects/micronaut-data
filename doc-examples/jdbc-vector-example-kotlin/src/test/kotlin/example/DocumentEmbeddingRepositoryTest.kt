package example

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class DocumentEmbeddingRepositoryTest {

    @Inject
    lateinit var repository: DocumentEmbeddingRepository

    @Test
    fun saveAndFetchEmbedding() {
        repository.deleteAll()

        val vec: Vector = Vector.of(0.1, 0.2, 0.3)

        val saved = repository.save(vec)
        repository.save(Vector.of(0.15, 0.2, 0.25))
        repository.save(Vector.of(0.9, 0.1, 0.1))

        assertNotNull(saved)
        assertNotNull(saved.id)

        val reloaded = repository.findById(saved.id!!).orElse(null)
        assertNotNull(reloaded)
        assertEquals(saved.id, reloaded!!.id)
        assertEquals(Double::class.javaPrimitiveType, reloaded.embedding.type)
        assertArrayEquals(doubleArrayOf(0.1, 0.2, 0.3), reloaded.embedding.toDoubleArray(), 1.0e-9)

        val near = repository.findTop2ByEmbeddingNear(vec, 2.0)
        assertEquals(2, near.size)
        assertTrue(near.any { it.id == saved.id })

        val scored = repository.searchByEmbeddingNear(vec, Score(2.0), ScoringFunction.COSINE)
        assertTrue(scored.results().isNotEmpty())
    }
}
