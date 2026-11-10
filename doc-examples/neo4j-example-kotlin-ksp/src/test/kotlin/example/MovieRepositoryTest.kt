package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MovieRepositoryTest : AbstractNeo4jTest() {

    @Inject
    lateinit var movieRepository: MovieRepository

    @AfterEach
    fun cleanup() {
        movieRepository.deleteAll()
    }

    @Test
    fun testRepositoryCompiledWithKsp() {
        val lana = Person("Lana Wachowski", 1965)
        movieRepository.save(Movie("The Matrix", 1999, listOf(lana)))
        movieRepository.save(Movie("Speed", 1994))

        assertEquals(listOf("The Matrix"), movieRepository.findByReleasedGreaterThan(1995).map { it.title })
        val directed = movieRepository.findByDirectorsName("Lana Wachowski")
        assertEquals(listOf("The Matrix"), directed.map { it.title })
        assertEquals(listOf("Lana Wachowski"), directed[0].directors.map { it.name })
    }
}
