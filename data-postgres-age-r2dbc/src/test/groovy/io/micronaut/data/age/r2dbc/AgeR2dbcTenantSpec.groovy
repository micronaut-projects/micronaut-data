package io.micronaut.data.age.r2dbc

import io.micronaut.context.annotation.Property
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

/**
 * A graph per tenant on R2DBC: the tenant is resolved when an operation is subscribed to, and names the graph.
 */
@MicronautTest(transactional = false)
@Property(name = "micronaut.data.multi-tenancy.mode", value = "SCHEMA")
@Property(name = "micronaut.data.age.tenant-graph-prefix", value = "reactive_")
@Property(name = "micronaut.data.age.tenant-graphs.globex", value = "reactive_globex_graph")
@Property(name = "micronaut.data.age.tenant-graphs.legacy", value = "reactive_tck")
@Property(name = "micronaut.data.age.tenant-graphs.pub", value = "public")
@Property(name = "micronaut.data.age.create-tenant-graphs", value = "true")
class AgeR2dbcTenantSpec extends Specification {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ConnectionFactory connectionFactory

    @Inject
    ReactorReactiveTransactionOperations<Connection> transactionOperations

    void setup() {
        for (tenant in ["acme", "globex", null]) {
            StaticTenantResolver.tenant = tenant
            movieRepository.deleteAll().block()
        }
    }

    void cleanup() {
        StaticTenantResolver.tenant = null
    }

    void "each tenant saves to and reads from its own graph"() {
        when:
        StaticTenantResolver.tenant = "acme"
        movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block()
        StaticTenantResolver.tenant = "globex"
        movieRepository.save(new Movie("The Matrix", "Welcome to the Real World", 1999)).block()

        then: "each tenant sees its own movie only"
        movieRepository.findAll().collectList().block()*.title == ["The Matrix"]
        !movieRepository.existsById("Speed").block()

        when:
        StaticTenantResolver.tenant = "acme"

        then:
        movieRepository.findAll().collectList().block()*.title == ["Speed"]
        !movieRepository.existsById("The Matrix").block()

        when: "the tenant is read when the operation is subscribed to, not when it is assembled"
        Mono<Boolean> exists = movieRepository.existsById("The Matrix")
        StaticTenantResolver.tenant = "globex"

        then:
        exists.block()

        when: "no tenant"
        StaticTenantResolver.tenant = null

        then: "the configured graph holds neither"
        movieRepository.count().block() == 0

        and: "the graphs are named after the tenants, through the prefix and the map"
        graphs().containsAll(["reactive_acme", "reactive_globex_graph"])
        !graphs().contains("reactive_globex")
    }

    void "a tenant whose graph name is not an identifier is rejected before any statement runs"() {
        given:
        StaticTenantResolver.tenant = "acme; DROP SCHEMA reactive_tck CASCADE; --"

        when:
        movieRepository.count().block()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("graph name")
        graphs().contains("reactive_tck")
    }

    void "a tenant whose graph is the configured graph or a reserved schema is rejected before any statement runs"() {
        given:
        StaticTenantResolver.tenant = tenant

        when:
        movieRepository.count().block()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("graph name [" + graph + "]")

        where:
        tenant   | graph
        "legacy" | "reactive_tck"
        "pub"    | "public"
    }

    void "the tenant is read from the Reactor context when the operation is subscribed to from an asynchronous callback"() {
        given:
        StaticTenantResolver.tenant = "acme"
        movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block()
        StaticTenantResolver.tenant = null
        def request = PropagatedContext.empty().plus(new StaticTenantResolver.Element("acme"))

        when: "the callback runs on another thread, with the request in the Reactor context only"
        boolean exists = Mono.delay(Duration.ofMillis(10))
            .flatMap(tick -> movieRepository.existsById("Speed"))
            .contextWrite(context -> ReactorPropagation.addPropagatedContext(context, request))
            .block()
        List<Movie> movies = Mono.delay(Duration.ofMillis(10))
            .flatMapMany(tick -> movieRepository.findAll())
            .contextWrite(context -> ReactorPropagation.addPropagatedContext(context, request))
            .collectList()
            .block()

        then:
        exists
        movies*.title == ["Speed"]

        when: "there is no request in the Reactor context"
        boolean noRequest = Mono.delay(Duration.ofMillis(10))
            .flatMap(tick -> movieRepository.existsById("Speed"))
            .block()

        then:
        !noRequest
    }

    void "a tenant graph created in a transaction that rolls back is created again by the next operation"() {
        given:
        if (graphs().contains("reactive_undone")) {
            Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement("SELECT ag_catalog.drop_graph('reactive_undone', true)").execute())
                    .concatMap(result -> result.rowsUpdated),
                connection -> connection.close()
            ).blockLast()
        }
        StaticTenantResolver.tenant = "undone"

        when: "a later operation of the transaction sees the graph created under a savepoint"
        transactionOperations.withTransactionMono { status ->
            movieRepository.findAll().collectList()
                .then(movieRepository.findAll().collectList())
                .then(Mono.fromRunnable { status.setRollbackOnly() })
        }.block()

        then: "the rollback undid the graph"
        !graphs().contains("reactive_undone")

        when:
        movieRepository.findAll().collectList().block()

        then:
        graphs().contains("reactive_undone")
    }

    private Set<String> graphs() {
        Flux.usingWhen(
            connectionFactory.create(),
            connection -> Flux.from(connection.createStatement("SELECT name FROM ag_catalog.ag_graph").execute())
                .flatMap(result -> result.map((row, metadata) -> row.get(0, String))),
            connection -> connection.close()
        ).collectList().block() as Set
    }
}
