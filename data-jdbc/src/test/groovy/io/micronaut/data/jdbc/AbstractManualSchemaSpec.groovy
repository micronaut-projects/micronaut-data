package io.micronaut.data.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Patient
import io.micronaut.data.tck.repositories.PatientRepository
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.transaction.jdbc.DelegatingDataSource
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

/**
 * This is the base test when need to create schema manually and test some features for jdbc. This was created to test getting auto generated ids when id is not first column,
 * but can be used for other purposes.
 */
abstract class AbstractManualSchemaSpec extends Specification {

    def LOG = LoggerFactory.getLogger(this.class)

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    DataSource dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource.class, Qualifiers.byName("default")))

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

    private void createSchema() {
        try {
            def conn = dataSource.getConnection()
            createStatements().forEach(st -> conn.prepareStatement(st).executeUpdate())
        } catch (Exception e) {
            LOG.warn("Error creating schema manually: " + e.getMessage())
        }
    }

    private void dropSchema() {
        try {
            def conn = dataSource.getConnection()
            dropStatements().forEach(st -> conn.prepareStatement(st).executeUpdate())
        } catch (Exception e) {
            LOG.warn("Error dropping schema manually: " + e.getMessage())
        }
    }

    private void insertRecord(String name, String history, String doctorNotes) {
        try {
            def conn = dataSource.getConnection()
            def insertStmt = conn.prepareStatement(insertStatement())
            insertStmt.setString(1, name)
            insertStmt.setString(2, history)
            insertStmt.setString(3, doctorNotes)
            def inserted = insertStmt.executeUpdate()
            assert inserted == 1
        } catch (Exception e) {
            LOG.warn("Error inserting record manually: " + e.getMessage())
        }
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
            insertRecord(name, history, doctorNotes)
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
