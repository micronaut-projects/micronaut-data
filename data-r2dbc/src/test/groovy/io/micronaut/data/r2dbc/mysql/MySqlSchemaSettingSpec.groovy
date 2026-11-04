package io.micronaut.data.r2dbc.mysql

import io.micronaut.data.r2dbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class MySqlSchemaSettingSpec extends AbstractSchemaSettingSpec implements MySqlTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "information_schema"
    }
}
