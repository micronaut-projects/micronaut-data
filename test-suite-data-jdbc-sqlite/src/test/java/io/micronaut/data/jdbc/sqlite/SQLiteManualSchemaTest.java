package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.tck.entities.Patient;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteManualSchemaTest {

    @Test
    void testSaveAndLoadRecordWhenIdNotFirstFieldInTheTable() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(createProperties())) {
            DataSource dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource.class, Qualifiers.byName("default")));
            SQLitePatientRepository patientRepository = context.getBean(SQLitePatientRepository.class);

            createSchema(dataSource);

            Patient patient = new Patient();
            patient.setName("Patient1");
            patient.setHistory("Enter some details");
            patientRepository.save(patient);

            var optPatient = patientRepository.findById(patient.getId());
            assertTrue(optPatient.isPresent());
            assertEquals(patient.getId(), optPatient.orElseThrow().getId());

            dropSchema(dataSource);
        }
    }

    @Disabled("FORMAT JSON")
    @Test
    void testManualInsertAndDtoRetrieval() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(createProperties())) {
            DataSource dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource.class, Qualifiers.byName("default")));
            SQLitePatientRepository patientRepository = context.getBean(SQLitePatientRepository.class);

            createSchema(dataSource);
            String name = "pt1";
            String history = "flu";
            String doctorNotes = "mild";
            List<String> appointments = List.of("Dr1 April 2022", "Dr2 June 2022");
            insertRecord(dataSource, name, history, doctorNotes);
            patientRepository.updateAppointmentsByName(name, appointments);

            var patientDtos = patientRepository.findAllByNameWithQuery(name);
            assertEquals(1, patientDtos.size());
            assertEquals(name, patientDtos.getFirst().getName());
            assertEquals(history, patientDtos.getFirst().getHistory());
            assertEquals(doctorNotes, patientDtos.getFirst().getDoctorNotes());
            assertEquals(appointments, patientDtos.getFirst().getAppointments());

            var optPatientDto = patientRepository.findByNameWithQuery(name);
            assertTrue(optPatientDto.isPresent());
            var patientDto = optPatientDto.orElseThrow();
            assertEquals(name, patientDto.getName());
            assertEquals(history, patientDto.getHistory());
            assertEquals(doctorNotes, patientDto.getDoctorNotes());
            assertEquals(appointments, patientDto.getAppointments());

            dropSchema(dataSource);
        }
    }

    private void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (String statement : List.of("CREATE TABLE patient (name TEXT,id INTEGER PRIMARY KEY,history TEXT,doctor_notes TEXT,appointments TEXT);")) {
                connection.prepareStatement(statement).executeUpdate();
            }
        }
    }

    private void dropSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (String statement : List.of("DROP TABLE patient")) {
                connection.prepareStatement(statement).executeUpdate();
            }
        }
    }

    private void insertRecord(DataSource dataSource, String name, String history, String doctorNotes) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            var insertStmt = connection.prepareStatement("INSERT INTO patient (name, history, doctor_notes) VALUES (?, ?, ?)");
            insertStmt.setString(1, name);
            insertStmt.setString(2, history);
            insertStmt.setString(3, doctorNotes);
            assertEquals(1, insertStmt.executeUpdate());
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqlitemanualschema", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "NONE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}
