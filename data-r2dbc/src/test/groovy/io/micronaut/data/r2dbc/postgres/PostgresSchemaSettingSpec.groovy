package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.r2dbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class PostgresSchemaSettingSpec extends AbstractSchemaSettingSpec implements PostgresTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "information_schema"
    }
}
