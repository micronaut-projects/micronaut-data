package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.PatientRepository

class SqlServerManualSchemaSpec extends AbstractManualSchemaSpec {

    @Override
    Dialect dialect() {
        Dialect.SQL_SERVER
    }

    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(MSPatientRepository)
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE TABLE [patient] ([name] VARCHAR(255), [id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL, [history] VARCHAR(1000));")
    }
}
