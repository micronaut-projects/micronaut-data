package io.micronaut.data.age

import io.micronaut.context.annotation.Property
import io.micronaut.data.age.features.Account
import io.micronaut.data.age.features.AccountRepository
import io.micronaut.data.age.features.Shelf
import io.micronaut.data.age.features.ShelfRepository
import io.micronaut.data.age.features.Volume
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
    ShelfRepository shelfRepository

    @Inject
    AgeRepositoryOperations operations

    @Inject
    DataSource dataSource

    void setup() {
        operations.executeQuery("MATCH (n:Account) DETACH DELETE n", [:])
        operations.executeQuery("MATCH (n:Shelf) DETACH DELETE n", [:])
        operations.executeQuery("MATCH (n:Volume) DETACH DELETE n", [:])
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

        and: "a node reached through a relationship is indexed without a repository of its own"
        indexes['Volume_code_unique'].startsWith('CREATE UNIQUE INDEX')
        indexes['Volume_isbn_unique'].startsWith('CREATE UNIQUE INDEX')

        and: "the version can be indexed"
        indexes['Volume_version_index'].contains('"version"')

        and: "generated names that only differ past the length of an identifier are told apart by a hash"
        def composite = indexes.findAll { it.key.startsWith('Volume_firstAuthorFamilyName') }
        composite.size() == 2
        composite.values().count { it.contains('"publisherCountryCode"') } == 1
        composite.values().count { it.contains('"publisherRegionCode"') } == 1
    }

    void "a new versioned node with an assigned identifier is inserted through a relationship"() {
        given:
        def volume = new Volume("v1", "978-1")

        when:
        shelfRepository.save(new Shelf(null, "Graphs", [volume]))

        then:
        volume.version == 0L
        operations.executeQuery("MATCH (v:Volume) RETURN v.version AS version", [:])*.asMap()*.get('version') == [0L]
    }

    void "a unique index of a node without a repository rejects a duplicate"() {
        given:
        shelfRepository.save(new Shelf(null, "Graphs", [new Volume("v1", "978-1")]))

        when:
        shelfRepository.save(new Shelf(null, "Other", [new Volume("v2", "978-1")]))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Volume_isbn_unique")
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
