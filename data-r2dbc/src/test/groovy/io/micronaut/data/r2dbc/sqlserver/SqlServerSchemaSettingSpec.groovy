package io.micronaut.data.r2dbc.sqlserver

import io.micronaut.data.r2dbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class SqlServerSchemaSettingSpec extends AbstractSchemaSettingSpec implements SqlServerTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "master"
    }
}
