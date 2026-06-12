package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.runtime.config.SchemaGenerate

/**
 * Used for tests that need Oracle 23+ target version options.
 */
trait Oracle23TestPropertyProvider extends OracleXETestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    Map<String, String> getProperties() {
        return super.getProperties() + [
            "r2dbc.datasources.default.dialect-options.version": "23.1"
        ]
    }
}
