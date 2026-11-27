package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchivedMovieRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var archivedMovieRepository: ArchivedMovieRepository

    @Inject
    lateinit var movieRepository: MovieRepository

    // Both drivers connect to the same server in the test
    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "neo4j.uri" to EmbeddedNeo4j.boltUri(),
        "micronaut.data.neo4j.drivers.archive.uri" to EmbeddedNeo4j.boltUri()
    )

    @AfterEach
    fun cleanup() {
        movieRepository.deleteAll()
    }

    @Test
    fun testArchiveDriver() {
        archivedMovieRepository.save(Movie("Metropolis", "The mediator between head and hands must be the heart", 1927))

        assertTrue(movieRepository.existsById("Metropolis"))
    }
}
