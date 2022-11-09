package io.micronaut.data.jdbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository

class SqlServerManualSchemaSpec extends AbstractManualSchemaSpec implements MSSQLTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(MSPatientRepository)
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE TABLE [patient] ([name] VARCHAR(255), [id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL, [history] VARCHAR(1000), [doctor_notes] VARCHAR(255));")
    }
}
