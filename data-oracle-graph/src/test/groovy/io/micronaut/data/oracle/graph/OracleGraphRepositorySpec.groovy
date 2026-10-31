package io.micronaut.data.oracle.graph

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.graph.tck.AbstractGraphRepositorySpec
import io.micronaut.data.graph.tck.repositories.ColleagueRepository
import io.micronaut.data.graph.tck.repositories.FriendRepository
import io.micronaut.data.oracle.graph.tck.OracleMovieRepository
import io.micronaut.data.oracle.graph.tck.OracleMovieSpecificationRepository
import io.micronaut.data.oracle.graph.tck.OraclePersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Shared

import javax.sql.DataSource
import java.sql.Connection

/**
 * Runs the graph TCK on Oracle Database 23ai, over a property graph of the movie and person vertex tables. Oracle
 * repositories write node rows only, and query with SQL/PGQ, so the features that need relationship writes or Cypher
 * are skipped.
 */
@MicronautTest(transactional = false)
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

    @Shared
    boolean created

    @Override
    void cleanupGraph() {
        if (!created) {
            sql("DROP PROPERTY GRAPH IF EXISTS tck",
                "DROP TABLE IF EXISTS movie",
                "DROP TABLE IF EXISTS person",
                "CREATE TABLE movie (title VARCHAR2(255) PRIMARY KEY, tagline VARCHAR2(255), released NUMBER(10), votes NUMBER(19))",
                "CREATE TABLE person (id VARCHAR2(36) PRIMARY KEY, name VARCHAR2(255), born NUMBER(10))",
                """CREATE PROPERTY GRAPH tck
                   VERTEX TABLES (movie KEY (title) LABEL Movie PROPERTIES (title, tagline, released, votes),
                                  person KEY (id) LABEL Person PROPERTIES (id, name, born))""")
            created = true
        }
        sql("DELETE FROM movie", "DELETE FROM person")
    }

    private void sql(String... statements) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statements.each { statement.execute(it) }
            }
        }
    }
}
