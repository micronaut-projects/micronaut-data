package example

import io.micronaut.data.neo4j.operations.Neo4jReactorRepositoryOperations
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class ReactiveMovieRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ReactiveMovieService movieService

    @Inject
    Neo4jReactorTransactionOperations transactionOperations

    @Inject
    Neo4jReactorRepositoryOperations reactiveOperations

    void cleanup() {
        movieRepository.deleteAll().block()
    }

    void "reactive repositories"() {
        when:
        // tag::reactive[]
        def titles = movieRepository.saveAll([ // <1>
                new Movie(title: "The Matrix", description: "Welcome to the Real World", released: 1999),
                new Movie(title: "Speed", description: "Get ready for rush hour", released: 1994)
            ])
            .thenMany(movieRepository.findByReleasedGreaterThan(1995)) // <2>
            .map { it.title }
            .collectList()
            .block() // <3>
        // end::reactive[]

        then:
        titles == ["The Matrix"]
    }

    void "reactive transactions"() {
        when:
        def count = movieService.saveAndCount(new Movie(title: "The Matrix", description: "Welcome to the Real World", released: 1999)).block()

        // tag::transactionOperations[]
        def saved = transactionOperations.withTransaction(TransactionDefinition.DEFAULT, { status -> // <1>
            movieRepository.saveAll([new Movie(title: "Speed", description: "Get ready for rush hour", released: 1994)])
        }).collectList().block()
        // end::transactionOperations[]

        then:
        count == 1
        saved.size() == 1
        movieRepository.count().block() == 2
    }

    void "reactive queries"() {
        given:
        movieRepository.save(new Movie(title: "Speed", description: "Get ready for rush hour", released: 1994)).block()

        when:
        // tag::reactiveQuery[]
        def movies = reactiveOperations.executeQuery('MATCH (m:Movie) RETURN m', [:], Movie) // <1>
            .collectList()
            .block()
        // end::reactiveQuery[]

        then:
        movies*.title == ["Speed"]
    }
}
