package io.micronaut.data.age.r2dbc

import io.micronaut.data.age.r2dbc.operations.AgeR2dbcRepositoryOperations
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

@MicronautTest(transactional = false)
class AgeR2dbcSpec extends Specification {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ReactivePersonRepository personRepository

    @Inject
    BlockingMovieRepository blockingMovieRepository

    @Inject
    AgeR2dbcRepositoryOperations operations

    @Inject
    ReactorReactiveTransactionOperations<Connection> transactionOperations

    void setup() {
        operations.reactive().executeQuery("MATCH (n) DETACH DELETE n", [:]).blockLast()
    }

    void "create, read, update and delete reactively"() {
        when:
        def saved = movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("The Matrix", "Welcome to the Real World", 1999)
        ]).collectList().block()

        then:
        saved*.title == ["Speed", "The Matrix"]
        movieRepository.count().block() == 2
        movieRepository.existsById("Speed").block()
        !movieRepository.existsById("Jaws").block()
        movieRepository.findById("The Matrix").block().released == 1999
        movieRepository.findById("Jaws").blockOptional().empty
        movieRepository.findByTitle("Speed").block().description == "Get ready for rush hour"
        movieRepository.findByReleasedGreaterThan(1995).collectList().block()*.title == ["The Matrix"]

        and: "asynchronous methods run on the reactive operations"
        movieRepository.countByReleasedGreaterThan(1995).get() == 1L

        when:
        def matrix = movieRepository.findById("The Matrix").block()
        matrix.votes = 42
        movieRepository.update(matrix).block()

        then:
        movieRepository.findById("The Matrix").block().votes == 42

        when:
        movieRepository.deleteById("Speed").block()

        then:
        movieRepository.count().block() == 1
    }

    void "pages and sorting"() {
        given:
        movieRepository.saveAll([
            new Movie("Speed", null, 1994),
            new Movie("The Matrix", null, 1999),
            new Movie("John Wick", null, 2014)
        ]).blockLast()

        when:
        def page = movieRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.desc("released")))).block()

        then:
        page.content*.title == ["John Wick", "The Matrix"]
        page.totalSize == 3
    }

    void "save relationships and traverse paths reactively"() {
        given:
        def lilly = personRepository.save(new Person("Lilly Wachowski", 1967)).block()
        def lana = new Person("Lana Wachowski", 1965)
        lana.mentor = lilly
        personRepository.save(lana).block()
        def keanu = new Person("Keanu Reeves", 1964)
        def matrix = new Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [new Role(["Neo"], keanu)]
        matrix.directors = [lana]

        when:
        def saved = movieRepository.save(matrix).block()

        then:
        saved.roles[0].id != null
        saved.roles[0].person.id != null
        count("MATCH (:Person {name: 'Keanu Reeves'})-[r:ACTED_IN]->(:Movie {title: 'The Matrix'}) RETURN count(r)") == 1
        count("MATCH (:Person {name: 'Lana Wachowski'})-[r:DIRECTED]->(:Movie {title: 'The Matrix'}) RETURN count(r)") == 1
        personRepository.findByNameStartsWith("Lana").collectList().block()*.name == ["Lana Wachowski"]
        personRepository.existsPathByMentorBetweenName("Lana Wachowski", "Lilly Wachowski").block()
        !personRepository.existsPathByMentorBetweenName("Lilly Wachowski", "Keanu Reeves").block()
    }

    void "commit and roll back reactive transactions"() {
        when:
        transactionOperations.withTransaction(status -> movieRepository.save(new Movie("Speed", null, 1994))).blockLast()

        then:
        movieRepository.existsById("Speed").block()

        when:
        transactionOperations.withTransaction(status ->
            movieRepository.save(new Movie("Jaws", null, 1975)).then(Mono.error(new IllegalStateException("rollback")))
        ).blockLast()

        then:
        thrown(IllegalStateException)
        !movieRepository.existsById("Jaws").block()
    }

    void "blocking methods are rejected"() {
        when:
        blockingMovieRepository.count()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("reactive")
    }

    private long count(String cypher) {
        operations.reactive().executeQuery(cypher, [:], Long).blockFirst()
    }
}
