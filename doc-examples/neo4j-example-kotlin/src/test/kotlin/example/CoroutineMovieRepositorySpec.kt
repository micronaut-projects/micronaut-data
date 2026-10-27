package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoroutineMovieRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var movieRepository: CoroutineMovieRepository

    @AfterEach
    fun cleanup() {
        runBlocking { movieRepository.deleteAll() }
    }

    @Test
    fun testCoroutines() = runBlocking {
        // tag::coroutines[]
        movieRepository.saveAll(listOf( // <1>
            Movie("The Matrix", "Welcome to the Real World", 1999),
            Movie("Speed", "Get ready for rush hour", 1994)
        )).toList()

        val matrix = movieRepository.findByTitle("The Matrix") // <2>
        val recent = movieRepository.findByReleasedGreaterThan(1995).map { it.title }.toList() // <3>
        // end::coroutines[]

        assertEquals(1999, matrix?.released)
        assertEquals(listOf("The Matrix"), recent)
        assertEquals(2L, movieRepository.count())
    }
}
