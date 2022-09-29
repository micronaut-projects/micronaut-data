package io.micronaut.data.jdbc.mysql

import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.PatientRepository


class MySqlManualSchemaSpec extends AbstractManualSchemaSpec {

    @Override
    Dialect dialect() {
        Dialect.MYSQL
    }

    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(MySqlPatientRepository)
    }
}
