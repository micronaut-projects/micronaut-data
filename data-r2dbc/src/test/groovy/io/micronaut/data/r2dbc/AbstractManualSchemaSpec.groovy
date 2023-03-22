package io.micronaut.data.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Patient
import io.micronaut.data.tck.repositories.PatientRepository
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Function

/**
 * This is the base test when need to create schema manually and test some features for r2dbc. This was created to test getting auto generated ids when id is not first column,
 * but can be used for other purposes.
 */
abstract class AbstractManualSchemaSpec extends Specification {

    def LOG = LoggerFactory.getLogger(this.class)

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    abstract PatientRepository getPatientRepository()

    List<String> createStatements() {
        // We want id on the second column to test scenario getting auto generated id not on the first position
        return Arrays.asList("CREATE TABLE patient(name VARCHAR(255), id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, history VARCHAR(1000), doctor_notes VARCHAR(255))")
    }

    List<String> dropStatements() {
        return Arrays.asList("DROP TABLE patient")
    }

    String insertStatement() {
        return "INSERT INTO patient (name, history, doctor_notes) VALUES (?, ?, ?)"
    }

    private Flux withConnection(@NonNull Function<Connection, Publisher> handler) {
        Objects.requireNonNull(handler, "Handler cannot be null")
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating a new Connection")
        }
        return Flux.usingWhen(connectionFactory.create(), handler, (connection -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing Connection")
            }
            return connection.close()
        }))
    }

    void createSchema() {
        Flux.from(withConnection(connection -> {
            Flux<Void> createTablesFlow = Flux.fromIterable(createStatements())
                    .concatMap(sql -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Creating Table: \n{}", sql);
                        }
                        return Flux.from(connection.createStatement(sql).execute()).onErrorResume((throwable -> {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Unable to create table :{}", throwable.getMessage());
                            }
                            return Mono.empty();
                        })).next();
                    });
            return createTablesFlow.then();
        })).blockLast()
    }

    void dropSchema() {
        Flux.from(withConnection(connection -> {
            Flux<Void> createTablesFlow = Flux.fromIterable(dropStatements())
                    .concatMap(sql -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Dropping Table: \n{}", sql);
                        }
                        return Flux.from(connection.createStatement(sql).execute()).onErrorResume((throwable -> {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Unable to drop table :{}", throwable.getMessage());
                            }
                            return Mono.empty();
                        })).next();
                    });
            return createTablesFlow.then();
        })).blockLast()
    }

    private Mono<Long> insertRecord(String name, String history, String doctorNotes) {
        return withConnection(connection -> {
            def insertStmt = connection.createStatement(insertStatement())
            insertStmt.bind(0, name)
            insertStmt.bind(1, history)
            insertStmt.bind(2, doctorNotes)
            return Flux.from(insertStmt.execute())
                    .flatMap(Result::getRowsUpdated)
                    .map((Number n) -> n.longValue())
        }).next()
    }

    void "test save and load record when id not first field in the table"() {
        given:
            createSchema()
            def patient = new Patient()
            patient.name = "Patient1"
            patient.history = "Enter some details"
            patientRepository.save(patient)
        when:
            def optPatient = patientRepository.findById(patient.id)
        then:
            optPatient.present
            optPatient.get().id == patient.id
        cleanup:
            dropSchema()
    }

    void "test manual insert and DTO retrieval"() {
        given:
            createSchema()
            def name = "pt1"
            def history = "flu"
            def doctorNotes = "mild"
            def inserted = insertRecord(name, history, doctorNotes).block()
            inserted == 1
        when:
            def patientDtos = patientRepository.findAllByNameWithQuery(name)
        then:
            patientDtos.size() == 1
            patientDtos[0].name == name
            patientDtos[0].history == history
            patientDtos[0].doctorNotes == doctorNotes
        when:
            def optPatientDto = patientRepository.findByNameWithQuery(name)
        then:
            optPatientDto.present
            optPatientDto.get().name == name
            optPatientDto.get().history == history
            optPatientDto.get().doctorNotes == doctorNotes
        cleanup:
            dropSchema()
    }
}
