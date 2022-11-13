package io.micronaut.data.jdbc.mysql

import groovy.transform.Memoized
import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository


class MySqlManualSchemaSpec extends AbstractManualSchemaSpec implements MySQLTestPropertyProvider  {

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
