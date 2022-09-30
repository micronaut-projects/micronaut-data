package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.jdbc.mysql.MySqlPatientRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.PatientRepository


class MariaManualSchemaSpec extends AbstractManualSchemaSpec {

    @Override
    Dialect dialect() {
        Dialect.MYSQL
    }

    @Override
    String driverName() {
        "mariadb"
    }

    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(MySqlPatientRepository)
    }
}
