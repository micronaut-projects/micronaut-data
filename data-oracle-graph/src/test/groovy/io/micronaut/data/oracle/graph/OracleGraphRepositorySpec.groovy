package io.micronaut.data.oracle.graph

import io.micronaut.context.annotation.Property
import io.micronaut.data.graph.tck.AbstractGraphRepositorySpec
import io.micronaut.data.graph.tck.repositories.ColleagueRepository
import io.micronaut.data.graph.tck.repositories.FriendRepository
import io.micronaut.data.oracle.graph.tck.OracleMovieRepository
import io.micronaut.data.oracle.graph.tck.OracleMovieSpecificationRepository
import io.micronaut.data.oracle.graph.tck.OraclePersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject

import javax.sql.DataSource
import java.sql.Connection

/**
 * Runs the graph TCK on Oracle Database 23ai, over the tables and property graph that schema generation creates for
 * the TCK entities. Oracle
 * repositories write node rows only, and query with SQL/PGQ, so the features that need relationship writes or Cypher
 * are skipped.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.graph.tck.entities")
class OracleGraphRepositorySpec extends AbstractGraphRepositorySpec {

    @Inject
    OracleMovieSpecificationRepository movieSpecificationRepository

    @Inject
    OracleMovieRepository movieRepository

    @Inject
    OraclePersonRepository personRepository

    @Inject
    TransactionOperations<Connection> transactionOperations

    @Inject
    DataSource dataSource

    @Override
    FriendRepository getFriendRepository() {
        null
    }

    @Override
    ColleagueRepository getColleagueRepository() {
        null
    }

    @Override
    boolean supportsRelationshipWrites() {
        false
    }

    @Override
    boolean supportsCypher() {
        false
    }

    @Override
    void cleanupGraph() {
        // Oracle repositories write node rows only, so the generated edge tables stay empty
        movieRepository.deleteAll()
        personRepository.deleteAll()
    }
}
