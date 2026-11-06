package io.micronaut.data.neo4j

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.neo4j.features.Account
import io.micronaut.data.neo4j.features.AccountRepository
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class Neo4jIndexSpec extends Specification implements TestPropertyProvider {

    @Inject
    AccountRepository accountRepository

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.create-indexes': 'true']
    }

    void setup() {
        operations.executeQuery("MATCH (n:Account) DETACH DELETE n", [:])
    }

    void "indexes and unique constraints are created at startup"() {
        when:
        def constraints = operations.executeQuery("SHOW CONSTRAINTS YIELD name, labelsOrTypes, properties RETURN name, labelsOrTypes, properties", [:])*.asMap()
        def indexes = operations.executeQuery("SHOW INDEXES YIELD name, labelsOrTypes, properties RETURN name, labelsOrTypes, properties", [:])*.asMap()

        then: "a unique index becomes a uniqueness constraint, as does an assigned identifier"
        constraints.find { it.name == 'Account_email_unique' }?.get('properties') == ['email']
        constraints.find { it.name == 'Movie_title_unique' }?.get('labelsOrTypes') == ['Movie']

        and: "the other indexes are property indexes"
        indexes.find { it.name == 'account_location' }?.get('properties') == ['country', 'city']
        indexes.find { it.name == 'Account_handle_index' }?.get('properties') == ['handle']
    }

    void "a unique index rejects a duplicate"() {
        given:
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        when:
        accountRepository.save(new Account("ada@example.com", "UK", "Cambridge", "lovelace"))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("already exists")
        accountRepository.count() == 1
    }
}
