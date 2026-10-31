package io.micronaut.data.age

import io.micronaut.context.annotation.Property
import io.micronaut.data.age.features.Account
import io.micronaut.data.age.features.AccountRepository
import io.micronaut.data.age.operations.AgeRepositoryOperations
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Types

@MicronautTest(transactional = false)
@Property(name = "micronaut.data.age.create-indexes", value = "true")
class AgeIndexSpec extends Specification {

    @Inject
    AccountRepository accountRepository

    @Inject
    AgeRepositoryOperations operations

    @Inject
    DataSource dataSource

    void setup() {
        operations.executeQuery("MATCH (n:Account) DETACH DELETE n", [:])
    }

    void "indexes are created on the vertex label tables at startup"() {
        when:
        Map<String, String> indexes = [:]
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            def statement = connection.prepareStatement("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'tck'")
            def resultSet = statement.executeQuery()
            while (resultSet.next()) {
                indexes[resultSet.getString(1)] = resultSet.getString(2)
            }
        }

        then:
        indexes['Account_email_unique'].startsWith('CREATE UNIQUE INDEX')
        indexes['account_location'].contains('"country"')
        indexes['account_location'].contains('"city"')
        !indexes['account_location'].contains('UNIQUE')
        indexes['Account_handle_index'].contains('"handle"')
        indexes['Movie_title_unique'].startsWith('CREATE UNIQUE INDEX')
    }

    void "a unique index rejects a duplicate"() {
        given:
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        when:
        accountRepository.save(new Account("ada@example.com", "UK", "Cambridge", "lovelace"))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Account_email_unique")
        accountRepository.count() == 1
    }

    void "a lookup by an indexed property scans the index"() {
        given:
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))
        accountRepository.save(new Account("grace@example.com", "US", "New York", "grace"))

        when: "sequential scans are ruled out, so the plan scans the index when AGE's property access matches it"
        String plan = DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute("LOAD 'age'")
                statement.execute('SET search_path = ag_catalog, "$user", public')
                statement.execute("SET enable_seqscan = off")
            }
            def explain = connection.prepareStatement('EXPLAIN SELECT * FROM cypher(\'tck\', $$ MATCH (n:Account) WHERE n.handle = $handle RETURN n $$, ?) AS (n agtype)')
            explain.setObject(1, '{"handle": "ada"}', Types.OTHER)
            def resultSet = explain.executeQuery()
            def lines = []
            while (resultSet.next()) {
                lines << resultSet.getString(1)
            }
            lines.join('\n')
        }

        then:
        plan.contains('Account_handle_index')
    }
}
