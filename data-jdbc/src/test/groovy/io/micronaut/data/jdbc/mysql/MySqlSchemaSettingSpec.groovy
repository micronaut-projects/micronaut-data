package io.micronaut.data.jdbc.mysql

import io.micronaut.data.jdbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class MySqlSchemaSettingSpec extends AbstractSchemaSettingSpec implements MySQLTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "information_schema"
    }
}
