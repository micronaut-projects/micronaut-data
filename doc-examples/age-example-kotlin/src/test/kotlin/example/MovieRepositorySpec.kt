package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class MovieRepositorySpec {

    @Inject
    lateinit var movieRepository: MovieRepository

    @AfterEach
    fun cleanup() {
        movieRepository.deleteAll()
    }

    @Test
    fun testSaveAndFind() {
        // tag::save[]
        val lana = Person(name = "Lana Wachowski", born = 1965)
        movieRepository.save(Movie(title = "The Matrix", released = 1999, directors = listOf(lana))) // <1>
        movieRepository.save(Movie(title = "The Matrix Reloaded", released = 2003, directors = listOf(lana)))

        val directedByLana = movieRepository.findByDirectorsName("Lana Wachowski") // <2>
        // end::save[]

        assertEquals(2, directedByLana.size)
        assertEquals(listOf("The Matrix Reloaded"), movieRepository.findByReleasedGreaterThanOrderByReleased(2000).map { it.title })
    }
}
