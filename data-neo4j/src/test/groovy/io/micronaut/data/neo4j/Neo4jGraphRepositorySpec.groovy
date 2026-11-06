package io.micronaut.data.neo4j

import io.micronaut.data.graph.tck.AbstractGraphRepositorySpec
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.tck.Neo4jMovieSpecificationRepository
import io.micronaut.data.neo4j.tck.Neo4jArticleRepository
import io.micronaut.data.neo4j.tck.Neo4jColleagueRepository
import io.micronaut.data.neo4j.tck.Neo4jFriendRepository
import io.micronaut.data.neo4j.tck.Neo4jMovieRepository
import io.micronaut.data.neo4j.tck.Neo4jPersonRepository
import io.micronaut.data.neo4j.transaction.Neo4jTransactionOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.TransactionUsageException
import io.micronaut.transaction.support.DefaultTransactionDefinition
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class Neo4jGraphRepositorySpec extends AbstractGraphRepositorySpec implements TestPropertyProvider {

    @Inject
    Neo4jMovieSpecificationRepository movieSpecificationRepository

    @Inject
    Neo4jArticleRepository articleRepository

    @Inject
    Neo4jMovieRepository movieRepository

    @Inject
    Neo4jPersonRepository personRepository

    @Inject
    Neo4jFriendRepository friendRepository

    @Inject
    Neo4jColleagueRepository colleagueRepository

    @Inject
    Neo4jTransactionOperations transactionOperations

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    @Override
    void cleanupGraph() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "a transaction that asks for an isolation level is rejected"() {
        given:
        def definition = new DefaultTransactionDefinition()
        definition.isolationLevel = TransactionDefinition.Isolation.SERIALIZABLE

        when:
        transactionOperations.execute(definition) { status -> operations.executeQuery("RETURN 1", [:]) }

        then:
        def e = thrown(TransactionUsageException)
        e.message.contains("isolation level")
    }
}
