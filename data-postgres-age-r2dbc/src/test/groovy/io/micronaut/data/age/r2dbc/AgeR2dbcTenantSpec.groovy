package io.micronaut.data.age.r2dbc

import io.micronaut.context.annotation.Property
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * A graph per tenant on R2DBC: the tenant is resolved when an operation is subscribed to, and names the graph.
 */
@MicronautTest(transactional = false)
@Property(name = "micronaut.data.multi-tenancy.mode", value = "SCHEMA")
@Property(name = "micronaut.data.age.tenant-graph-prefix", value = "reactive_")
@Property(name = "micronaut.data.age.tenant-graphs.globex", value = "reactive_globex_graph")
class AgeR2dbcTenantSpec extends Specification {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ConnectionFactory connectionFactory

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

    private Set<String> graphs() {
        Flux.usingWhen(
            connectionFactory.create(),
            connection -> Flux.from(connection.createStatement("SELECT name FROM ag_catalog.ag_graph").execute())
                .flatMap(result -> result.map((row, metadata) -> row.get(0, String))),
            connection -> connection.close()
        ).collectList().block() as Set
    }
}
