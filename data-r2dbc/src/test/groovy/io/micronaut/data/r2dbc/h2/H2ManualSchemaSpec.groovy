package io.micronaut.data.r2dbc.h2

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.AbstractManualSchemaSpec
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository


class H2ManualSchemaSpec extends AbstractManualSchemaSpec implements H2TestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(H2PatientRepository)
    }
}
