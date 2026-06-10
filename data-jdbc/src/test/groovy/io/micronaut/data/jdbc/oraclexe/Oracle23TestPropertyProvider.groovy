package io.micronaut.data.jdbc.oraclexe

import io.micronaut.data.runtime.config.SchemaGenerate

/**
 * Used for tests that need Oracle 23+ compatibility options.
 */
trait Oracle23TestPropertyProvider extends OracleTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    Map<String, String> getProperties() {
        return super.getProperties() + [
            "datasources.default.dialect-options.compatibility": "ORACLE_23"
        ]
    }
}
