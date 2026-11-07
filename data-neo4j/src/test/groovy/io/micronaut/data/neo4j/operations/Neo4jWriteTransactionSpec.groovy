package io.micronaut.data.neo4j.operations

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Repository
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.support.AbstractConnectionOperations
import io.micronaut.data.connection.support.ConnectionCustomizer
import io.micronaut.data.neo4j.EmbeddedNeo4j
import io.micronaut.data.neo4j.features.Shelf
import io.micronaut.data.neo4j.features.ShelfRepository
import io.micronaut.data.neo4j.session.Neo4jConnection
import io.micronaut.data.neo4j.session.Neo4jConnectionOperations
import io.micronaut.data.neo4j.session.Neo4jReactorConnectionOperations
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionDefinition
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.function.Function

/**
 * The transaction a write begins carries the annotation metadata of the operation, so that per-method connection
 * settings apply to it.
 */
@MicronautTest(transactional = false)
class Neo4jWriteTransactionSpec extends Specification implements TestPropertyProvider {

    @Inject
    ShelfRepository shelfRepository

    @Inject
    Neo4jConnectionOperations connectionOperations

    @Inject
    Neo4jReactorConnectionOperations reactorConnectionOperations

    @Inject
    DefaultNeo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void "the connection of a write carries the annotation metadata of the operation"() {
        given:
        List<AnnotationMetadata> opened = []
        ((AbstractConnectionOperations<Neo4jConnection>) connectionOperations).addConnectionCustomizer(new ConnectionCustomizer<Neo4jConnection>() {
            @Override
            <R> Function<ConnectionStatus<Neo4jConnection>, R> intercept(Function<ConnectionStatus<Neo4jConnection>, R> operation) {
                return operation
            }

            @Override
            void connectionOpened(ConnectionStatus<Neo4jConnection> status) {
                opened << status.definition.annotationMetadata
            }
        })

        when:
        shelfRepository.save(new Shelf(null, "Graphs", []))

        then:
        opened.size() == 1
        opened[0].hasStereotype(Repository)
    }

    void "the transaction of a reactive write carries the annotation metadata of the operation"() {
        given:
        def metadata = new MutableAnnotationMetadata()
        List<TransactionDefinition> definitions = []
        def transactions = Stub(Neo4jReactorTransactionOperations) {
            withTransactionMono(_, _) >> { args ->
                definitions << (TransactionDefinition) args[0]
                Mono.empty()
            }
            withTransactionFlux(_, _) >> { args ->
                definitions << (TransactionDefinition) args[0]
                Flux.empty()
            }
        }
        def reactive = new DefaultNeo4jReactorRepositoryOperations(operations, reactorConnectionOperations, transactions)

        when:
        reactive.withTransactionMono(metadata, session -> Mono.empty()).block()
        reactive.withTransactionFlux(metadata, session -> Flux.empty()).blockLast()

        then:
        definitions.size() == 2
        definitions.every { it.connectionDefinition.annotationMetadata.is(metadata) }
    }
}
