package io.micronaut.data.age

import io.micronaut.data.age.operations.AgeRepositoryOperations
import io.micronaut.data.age.tck.AgeArticleRepository
import io.micronaut.data.age.tck.AgeColleagueRepository
import io.micronaut.data.age.tck.AgeFriendRepository
import io.micronaut.data.age.tck.AgeMovieRepository
import io.micronaut.data.age.tck.AgePersonRepository
import io.micronaut.data.graph.tck.AbstractGraphRepositorySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject

import java.sql.Connection

/**
 * Runs the graph TCK against PostgreSQL with Apache AGE, started by Micronaut Test Resources.
 */
@MicronautTest(transactional = false)
class AgeGraphRepositorySpec extends AbstractGraphRepositorySpec {

    @Inject
    AgeArticleRepository articleRepository

    @Inject
    AgeMovieRepository movieRepository

    @Inject
    AgePersonRepository personRepository

    @Inject
    AgeFriendRepository friendRepository

    @Inject
    AgeColleagueRepository colleagueRepository

    @Inject
    TransactionOperations<Connection> transactionOperations

    @Inject
    AgeRepositoryOperations operations

    @Override
    void cleanupGraph() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }
}
