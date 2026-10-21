package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class SqlServerSchemaSettingSpec extends AbstractSchemaSettingSpec implements MSSQLTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "master"
    }
}
