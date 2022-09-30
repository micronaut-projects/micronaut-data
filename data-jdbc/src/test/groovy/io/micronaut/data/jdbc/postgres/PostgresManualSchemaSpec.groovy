package io.micronaut.data.jdbc.postgres

import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.PatientRepository

class PostgresManualSchemaSpec extends AbstractManualSchemaSpec {

    @Override
    Dialect dialect() {
        Dialect.POSTGRES
    }

    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(PostgresPatientRepository)
    }
}
