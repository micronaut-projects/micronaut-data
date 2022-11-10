package io.micronaut.data.r2dbc.mysql

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.AbstractManualSchemaSpec
import io.micronaut.data.r2dbc.h2.H2PatientRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository

class MySqlManualSchemaSpec extends AbstractManualSchemaSpec implements MySqlTestPropertyProvider {

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
