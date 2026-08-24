package io.micronaut.data.jdbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.tck.entities.Patient;
import io.micronaut.data.tck.repositories.PatientRepository;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is the base test when need to create schema manually and test some features for jdbc. This was created to test getting auto generated ids when id is not first column,
 * but can be used for other purposes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractManualSchemaTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ApplicationContext context = ApplicationContext.run(new java.util.HashMap<>(getProperties()));

    protected final DataSource dataSource = DelegatingDataSource.unwrapDataSource(
        context.getBean(DataSource.class, Qualifiers.byName("default"))
    );

    protected abstract PatientRepository getPatientRepository();

    protected abstract java.util.Map<String, String> getProperties();

    protected List<String> createStatements() {
        return Arrays.asList("CREATE TABLE patient (name TEXT,id INTEGER PRIMARY KEY,history TEXT,doctor_notes TEXT,appointments TEXT);");
    }

    protected List<String> dropStatements() {
        return Arrays.asList("DROP TABLE patient");
    }

    protected String insertStatement() {
        return "INSERT INTO patient (name, history, doctor_notes) VALUES (?, ?, ?)";
    }

    protected void createSchema() {
        try {
            var conn = dataSource.getConnection();
            createStatements().forEach(st -> {
                try {
                    conn.prepareStatement(st).executeUpdate();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            log.warn("Error creating schema manually: {}", e.getMessage());
        }
    }

    protected void dropSchema() {
        try {
            var conn = dataSource.getConnection();
            dropStatements().forEach(st -> {
                try {
                    conn.prepareStatement(st).executeUpdate();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            log.warn("Error dropping schema manually: {}", e.getMessage());
        }
    }

    private void insertRecord(String name, String history, String doctorNotes) {
        try {
            var conn = dataSource.getConnection();
            var insertStmt = conn.prepareStatement(insertStatement());
            insertStmt.setString(1, name);
            insertStmt.setString(2, history);
            insertStmt.setString(3, doctorNotes);
            int inserted = insertStmt.executeUpdate();
            assertEquals(1, inserted);
        } catch (Exception e) {
            log.warn("Error inserting record manually: {}", e.getMessage());
        }
    }

    @AfterAll
    void closeContext() {
        context.close();
    }

    @Test
    void testSaveAndLoadRecordWhenIdNotFirstFieldInTheTable() {
        createSchema();
        try {
            Patient patient = new Patient();
            patient.setName("Patient1");
            patient.setHistory("Enter some details");
            getPatientRepository().save(patient);

            var optPatient = getPatientRepository().findById(patient.getId());

            assertTrue(optPatient.isPresent());
            assertEquals(patient.getId(), optPatient.get().getId());
        } finally {
            dropSchema();
        }
    }

    @Test
    @Disabled("FORMAT JSON")
    void testManualInsertAndDtoRetrieval() {
        createSchema();
        try {
            String name = "pt1";
            String history = "flu";
            String doctorNotes = "mild";
            List<String> appointments = List.of("Dr1 April 2022", "Dr2 June 2022");
            insertRecord(name, history, doctorNotes);
            getPatientRepository().updateAppointmentsByName(name, appointments);

            var patientDtos = getPatientRepository().findAllByNameWithQuery(name);

            assertEquals(1, patientDtos.size());
            assertEquals(name, patientDtos.get(0).getName());
            assertEquals(history, patientDtos.get(0).getHistory());
            assertEquals(doctorNotes, patientDtos.get(0).getDoctorNotes());
            assertEquals(appointments, patientDtos.get(0).getAppointments());

            var optPatientDto = getPatientRepository().findByNameWithQuery(name);

            assertTrue(optPatientDto.isPresent());
            var patientDto = optPatientDto.get();
            assertEquals(name, patientDto.getName());
            assertEquals(history, patientDto.getHistory());
            assertEquals(doctorNotes, patientDto.getDoctorNotes());
            assertEquals(appointments, patientDto.getAppointments());
        } finally {
            dropSchema();
        }
    }
}
