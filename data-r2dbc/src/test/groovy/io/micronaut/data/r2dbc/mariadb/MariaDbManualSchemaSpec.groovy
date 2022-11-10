package io.micronaut.data.r2dbc.mariadb

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.AbstractManualSchemaSpec
import io.micronaut.data.r2dbc.mysql.MySqlPatientRepository
import io.micronaut.data.r2dbc.mysql.MySqlTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository

class MariaDbManualSchemaSpec extends AbstractManualSchemaSpec implements MariaDbTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(MySqlPatientRepository)
    }
}
