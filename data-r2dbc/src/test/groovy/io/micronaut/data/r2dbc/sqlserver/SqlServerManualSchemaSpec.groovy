package io.micronaut.data.r2dbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.AbstractManualSchemaSpec
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository

class SqlServerManualSchemaSpec extends AbstractManualSchemaSpec implements SqlServerTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE TABLE [patient] ([name] VARCHAR(255), [id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL, [history] VARCHAR(1000), [doctor_notes] VARCHAR(255), [appointments] NVARCHAR(MAX));")
    }

    @Override
    String insertStatement() {
        return "INSERT INTO patient (name, history, doctor_notes) VALUES (@p1, @p2, @p3)"
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(MSPatientRepository)
    }
}
