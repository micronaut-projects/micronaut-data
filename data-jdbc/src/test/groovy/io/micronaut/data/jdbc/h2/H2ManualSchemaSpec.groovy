package io.micronaut.data.jdbc.h2

import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.PatientRepository
import org.testcontainers.containers.JdbcDatabaseContainer


class H2ManualSchemaSpec extends AbstractManualSchemaSpec {

    @Override
    Dialect dialect() {
        Dialect.H2
    }

    @Override
    String jdbcUrl(JdbcDatabaseContainer container) {
        "jdbc:h2:mem:mydb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
    }

    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(H2PatientRepository)
    }
}
