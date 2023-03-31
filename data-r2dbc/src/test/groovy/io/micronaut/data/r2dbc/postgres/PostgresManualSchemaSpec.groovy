package io.micronaut.data.r2dbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.AbstractManualSchemaSpec
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository

class PostgresManualSchemaSpec extends AbstractManualSchemaSpec implements PostgresTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(PostgresPatientRepository)
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE TABLE patient(name VARCHAR(255), id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, history VARCHAR(1000), doctor_notes VARCHAR(255), appointments JSONB)")
    }

    @Override
    String insertStatement() {
        return "INSERT INTO patient (name, history, doctor_notes) VALUES (\$1, \$2, \$3)"
    }
}
