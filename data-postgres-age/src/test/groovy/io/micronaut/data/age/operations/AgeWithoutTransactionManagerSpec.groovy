package io.micronaut.data.age.operations

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.data.age.tck.AgeMovieRepository
import io.micronaut.data.age.tck.AgePersonRepository
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * A data source whose transactions another transaction manager, such as that of JPA, manages has no JDBC transaction
 * manager: its writes commit or roll back on their own connection.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.transaction-manager", value = "hibernate")
class AgeWithoutTransactionManagerSpec extends Specification {

    @Inject
    ApplicationContext context

    @Inject
    AgeRepositoryOperations operations

    @Inject
    AgeMovieRepository movieRepository

    @Inject
    AgePersonRepository personRepository

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "a write commits without a transaction manager"() {
        expect:
        !context.containsBean(TransactionOperations)

        when:
        def movie = new Movie("Speed", null, 1994)
        movie.roles = [new Role(["Jack"], new Person("Keanu Reeves", 1964))]
        movieRepository.save(movie)

        then:
        movieRepository.existsById("Speed")
        personRepository.count() == 1
    }

    void "a write that fails rolls back without a transaction manager"() {
        given:
        def movie = new Movie("Bound", null, 1996)
        movie.roles = [new Role(["Corky"], new Person("Gina Gershon", 1962)), new Role(["Violet"], null)]

        when:
        movieRepository.save(movie)

        then:
        thrown(DataAccessException)
        !movieRepository.existsById("Bound")
        personRepository.count() == 0
    }
}
