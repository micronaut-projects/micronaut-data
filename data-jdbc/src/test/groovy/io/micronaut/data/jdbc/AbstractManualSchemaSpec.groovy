package io.micronaut.data.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.runtime.config.SchemaGenerate
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
 * This is base test when need to create schema manually and test some features. This was created to test getting auto generated ids when id is not first column,
 * but can be used for other purposes.
 */
abstract class AbstractManualSchemaSpec extends Specification implements DatabaseTestPropertyProvider {

    def logger = LoggerFactory.getLogger(this.class)

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    DataSource dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource.class, Qualifiers.byName("default")));

    abstract PatientRepository getPatientRepository()

    List<String> createStatements() {
        // We want id on the second column to test scenario getting auto generated id not on the first position
        return Arrays.asList("CREATE TABLE patient(name VARCHAR(255), id SERIAL NOT NULL PRIMARY KEY, history VARCHAR(1000))")
    }

    List<String> dropStatements() {
        return Arrays.asList("DROP TABLE patient")
    }

    private void createSchema() {
        try {
            def conn = dataSource.getConnection();
            createStatements().forEach(st -> conn.prepareStatement(st).executeUpdate())
        } catch (Exception e) {
            logger.warn("Error creating schema manually: " + e.getMessage())
        }
    }

    private void dropSchema() {
        try {
            def conn = dataSource.getConnection();
            dropStatements().forEach(st -> conn.prepareStatement(st).executeUpdate())
        } catch (Exception e) {
            logger.warn("Error dropping schema manually: " + e.getMessage())
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
}
