package io.micronaut.data.age.r2dbc.operations

import io.micronaut.data.connection.reactive.ReactorConnectionOperations
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * The transaction a reactive write begins carries the annotation metadata of the operation, so that per-method
 * connection settings apply to it.
 */
@MicronautTest(transactional = false)
class AgeR2dbcWriteTransactionSpec extends Specification {

    @Inject
    DefaultAgeR2dbcRepositoryOperations operations

    @Inject
    ReactorConnectionOperations<Connection> connectionOperations

    @Inject
    JsonMapper jsonMapper

    void "the transaction of a reactive write carries the annotation metadata of the operation"() {
        given:
        def metadata = new MutableAnnotationMetadata()
        List<TransactionDefinition> definitions = []
        def transactions = Stub(R2dbcReactorTransactionOperations) {
            withTransactionMono(_, _) >> { args ->
                definitions << (TransactionDefinition) args[0]
                Mono.empty()
            }
            withTransactionFlux(_, _) >> { args ->
                definitions << (TransactionDefinition) args[0]
                Flux.empty()
            }
        }
        def reactive = new DefaultAgeReactorRepositoryOperations(operations, connectionOperations, transactions, jsonMapper, "tck", null, false, false, false)

        when:
        reactive.withTransactionMono(metadata, session -> Mono.empty()).block()
        reactive.withTransactionFlux(metadata, session -> Flux.empty()).blockLast()

        then:
        definitions.size() == 2
        definitions.every { it.connectionDefinition.annotationMetadata.is(metadata) }
    }
}
