package example

import io.micronaut.data.neo4j.operations.Neo4jReactorRepositoryOperations
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
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

    @Inject
    lateinit var movieService: ReactiveMovieService

    @Inject
    lateinit var transactionOperations: Neo4jReactorTransactionOperations

    @Inject
    lateinit var reactiveOperations: Neo4jReactorRepositoryOperations

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

    @Test
    fun testTransactions() {
        val count = movieService.saveAndCount(Movie("The Matrix", "Welcome to the Real World", 1999)).block()

        // tag::transactionOperations[]
        val saved = transactionOperations.withTransaction(TransactionDefinition.DEFAULT) { // <1>
            movieRepository.saveAll(listOf(Movie("Speed", "Get ready for rush hour", 1994)))
        }.collectList().block()
        // end::transactionOperations[]

        assertEquals(1L, count)
        assertEquals(1, saved!!.size)
        assertEquals(2L, movieRepository.count().block())
    }

    @Test
    fun testReactiveQuery() {
        movieRepository.save(Movie("Speed", "Get ready for rush hour", 1994)).block()

        // tag::reactiveQuery[]
        val movies = reactiveOperations.executeQuery("MATCH (m:Movie) RETURN m", mapOf(), Movie::class.java) // <1>
            .collectList()
            .block()
        // end::reactiveQuery[]

        assertEquals("Speed", movies!![0].title)
    }
}
