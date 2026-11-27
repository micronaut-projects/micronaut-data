package io.micronaut.data.age.operations

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.age.features.Account
import io.micronaut.data.age.features.AccountRepository
import io.micronaut.data.annotation.Repository
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.support.AbstractConnectionOperations
import io.micronaut.data.connection.support.ConnectionCustomizer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.Connection
import java.util.function.Function

/**
 * The transaction a write begins carries the annotation metadata of the operation, so that per-method connection
 * settings apply to it.
 */
@MicronautTest(transactional = false)
class AgeWriteTransactionSpec extends Specification {

    @Inject
    AccountRepository accountRepository

    @Inject
    ConnectionOperations<Connection> connectionOperations

    void "the connection of a write carries the annotation metadata of the operation"() {
        given:
        accountRepository.deleteAll()
        List<AnnotationMetadata> opened = []
        ((AbstractConnectionOperations<Connection>) connectionOperations).addConnectionCustomizer(new ConnectionCustomizer<Connection>() {
            @Override
            <R> Function<ConnectionStatus<Connection>, R> intercept(Function<ConnectionStatus<Connection>, R> operation) {
                return operation
            }

            @Override
            void connectionOpened(ConnectionStatus<Connection> status) {
                opened << status.definition.annotationMetadata
            }
        })

        when:
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        then:
        opened.size() == 1
        opened[0].hasStereotype(Repository)
    }
}
