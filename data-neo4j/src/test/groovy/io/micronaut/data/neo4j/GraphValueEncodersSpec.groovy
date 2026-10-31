package io.micronaut.data.neo4j

import io.micronaut.data.graph.runtime.GraphValueEncoders
import io.micronaut.data.neo4j.features.Money
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.Temporal

@MicronautTest(transactional = false)
class GraphValueEncodersSpec extends Specification implements TestPropertyProvider {

    enum Colour { RED }

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void "encode values with the nearest registered encoder"() {
        given:
        def encoders = GraphValueEncoders.defaults()
        def instant = Instant.parse("2026-10-15T10:15:30Z")

        expect:
        encoders.encode(null) == null
        encoders.encode("a") == "a"
        encoders.encode(Colour.RED) == "RED"
        encoders.encode(instant) == instant.atOffset(ZoneOffset.UTC)
        encoders.encode(LocalDate.of(2026, 10, 15)) == LocalDate.of(2026, 10, 15)
        encoders.encode(new Date(instant.toEpochMilli())) == instant.atOffset(ZoneOffset.UTC)
        encoders.encode(new BigDecimal("1.50")) == "1.50"
        encoders.encode([1, Colour.RED, [UUID.fromString("00000000-0000-0000-0000-000000000001")]] as Object[]) ==
            [1, "RED", ["00000000-0000-0000-0000-000000000001"]]
        encoders.encode([a: [Colour.RED] as Set]) == [a: ["RED"]]
        encoders.encode(new StringBuilder("sb")) == "sb"
    }

    void "a database replaces the default encoders"() {
        given:
        def encoders = GraphValueEncoders.defaults()
            .register(Temporal, (value, e) -> value.toString())
            .register(Instant, (value, e) -> value.toString())

        expect:
        encoders.encode(Instant.parse("2026-10-15T10:15:30Z")) == "2026-10-15T10:15:30Z"
        encoders.encode(OffsetDateTime.parse("2026-10-15T10:15:30+01:00")) == "2026-10-15T10:15:30+01:00"
    }

    void "encoders registered by the application encode parameters"() {
        when:
        def rows = operations.executeQuery('RETURN $money.amount AS amount, $money.currency AS currency',
            [money: new Money(new BigDecimal("9.99"), "EUR")])

        then:
        rows[0].get("amount") == "9.99"
        rows[0].get("currency") == "EUR"
    }
}
