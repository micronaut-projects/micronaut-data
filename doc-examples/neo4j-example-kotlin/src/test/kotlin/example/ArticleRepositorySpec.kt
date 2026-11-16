package example

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var articleRepository: ArticleRepository

    @AfterEach
    fun cleanup() {
        articleRepository.deleteAll()
    }

    @Test
    fun testOptimisticLocking() {
        // tag::optimisticLocking[]
        articleRepository.save(Article("graphs", "Graphs", "Ada")) // <1>
        val first = articleRepository.findById("graphs").get()
        val second = articleRepository.findById("graphs").get()

        first.title = "Graph databases"
        articleRepository.update(first) // <2>

        second.title = "Graphs everywhere"
        assertThrows<OptimisticLockException> { articleRepository.update(second) } // <3>
        // end::optimisticLocking[]

        val stored = articleRepository.findById("graphs").get()
        assertEquals("Graph databases", stored.title)
        assertEquals(1L, stored.version)
        assertEquals(listOf("graphs"), articleRepository.findByAuthor("Ada").map { it.slug })
    }
}
