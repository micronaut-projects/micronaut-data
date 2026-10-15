package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactiveMovieRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var movieRepository: ReactiveMovieRepository

    @AfterEach
    fun cleanup() {
        movieRepository.deleteAll().block()
    }

    @Test
    fun testReactive() {
        // tag::reactive[]
        val titles = movieRepository.saveAll(listOf( // <1>
                Movie("The Matrix", "Welcome to the Real World", 1999),
                Movie("Speed", "Get ready for rush hour", 1994)
            ))
            .thenMany(movieRepository.findByReleasedGreaterThan(1995)) // <2>
            .map { it.title }
            .collectList()
            .block() // <3>
        // end::reactive[]

        assertEquals(listOf("The Matrix"), titles)
    }
}
