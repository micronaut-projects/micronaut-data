package io.micronaut.data.age.r2dbc

import io.micronaut.context.annotation.Property
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * By default a tenant id cannot create a graph, which is a schema of the database: the tenant id may come from a request.
 */
@MicronautTest(transactional = false)
@Property(name = "micronaut.data.multi-tenancy.mode", value = "SCHEMA")
@Property(name = "micronaut.data.age.tenant-graph-prefix", value = "reactive_absent_")
class AgeR2dbcTenantGraphAbsentSpec extends Specification {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ConnectionFactory connectionFactory

    @Inject
    ReactorReactiveTransactionOperations<Connection> transactionOperations

    void cleanup() {
        StaticTenantResolver.tenant = null
    }

    void "a tenant whose graph does not exist fails, and does not create it"() {
        given:
        StaticTenantResolver.tenant = "unknown"

        when: "a read"
        movieRepository.count().block()

        then:
        def read = thrown(DataAccessException)
        read.message.contains("create-tenant-graphs")

        when: "a write"
        movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block()

        then:
        def write = thrown(DataAccessException)
        write.message.contains("create-tenant-graphs")

        when: "in a transaction"
        transactionOperations.withTransactionMono { status -> movieRepository.count() }.block()

        then:
        def transactional = thrown(DataAccessException)
        transactional.message.contains("create-tenant-graphs")

        and:
        !rows("SELECT name FROM ag_catalog.ag_graph").contains("reactive_absent_unknown")
    }

    void "a tenant whose graph exists uses it"() {
        given:
        rows("SELECT ag_catalog.drop_graph(name, true)::text FROM ag_catalog.ag_graph WHERE name = 'reactive_absent_provisioned'")
        rows("SELECT ag_catalog.create_graph('reactive_absent_provisioned')::text")

        when:
        StaticTenantResolver.tenant = "provisioned"
        movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block()

        then:
        movieRepository.findAll().collectList().block()*.title == ["Speed"]
    }

    private List<String> rows(String sql) {
        Flux.usingWhen(
            connectionFactory.create(),
            connection -> Flux.from(connection.createStatement(sql).execute())
                .flatMap(result -> result.map((row, metadata) -> Optional.ofNullable(row.get(0, String)))),
            connection -> connection.close()
        ).collectList().block()*.orElse(null)
    }
}
