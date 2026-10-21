package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class MariaSchemaSettingSpec extends AbstractSchemaSettingSpec implements MariaTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "information_schema"
    }
}
