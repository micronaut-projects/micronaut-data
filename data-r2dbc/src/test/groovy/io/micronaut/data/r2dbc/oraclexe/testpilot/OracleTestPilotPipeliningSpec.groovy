package io.micronaut.data.r2dbc.oraclexe.testpilot

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

/**
 * Checks on an Oracle Test Pilot database, over a real network, that independent repository operations composed
 * concurrently in one transaction are pipelined on its connection: they take less time than the same operations in
 * sequence. A local database has too little latency to tell.
 */
@IgnoreIf({ !env.TESTPILOT_USERNAME })
class OracleTestPilotPipeliningSpec extends Specification {

    static final int LOOKUPS = 20

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
        // TestPilotDescriptor replaces the options of this URL with the Test Pilot database
        "r2dbc.datasources.default.url"            : "r2dbc:oracle://testpilot",
        "r2dbc.datasources.default.username"       : System.getenv("TESTPILOT_USERNAME"),
        "r2dbc.datasources.default.password"       : System.getenv("TESTPILOT_PASSWORD"),
        (TestPilotDescriptor.PREFIX + ".descriptor"): System.getenv("TESTPILOT_CONNECTION_STRING_SUFFIX"),
        (TestPilotDescriptor.PREFIX + ".user")      : System.getenv("TESTPILOT_USERNAME"),
        (TestPilotDescriptor.PREFIX + ".password")  : System.getenv("TESTPILOT_PASSWORD"),
        "r2dbc.datasources.default.dialect"        : "ORACLE",
        "r2dbc.datasources.default.schema-generate": "CREATE_DROP",
        "r2dbc.datasources.default.packages"       : OracleTestPilotPipeliningSpec.package.name
    ])

    @Shared
    MeasurementRepository repository = context.getBean(MeasurementRepository)

    @Shared
    R2dbcOperations operations = context.getBean(R2dbcOperations)

    void "concurrent repository operations in a transaction are pipelined"() {
        given:
        repository.save(new Measurement(1L, "latency")).block()
        // warm up the statement and the connection pool
        2.times { timed(false) }

        when:
        Duration sequential = (1..3).collect { timed(false) }.min()
        Duration concurrent = (1..3).collect { timed(true) }.min()
        println "Oracle Test Pilot: ${LOOKUPS} lookups in sequence took ${sequential.toMillis()} ms, concurrently ${concurrent.toMillis()} ms"

        then:
        concurrent.toNanos() < sequential.toNanos() * 0.7
    }

    private Duration timed(boolean concurrently) {
        long start = System.nanoTime()
        Mono.from(operations.withTransaction { status ->
            Flux<Measurement> lookups = concurrently
                ? Flux.range(0, LOOKUPS).flatMap({ repository.findById(1L) }, LOOKUPS)
                : Flux.range(0, LOOKUPS).concatMap({ repository.findById(1L) })
            lookups.collectList()
        }).block()
        Duration.ofNanos(System.nanoTime() - start)
    }
}
