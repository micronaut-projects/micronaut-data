package io.micronaut.data.r2dbc.mariadb

import io.micronaut.data.r2dbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class MariaDbSchemaSettingSpec extends AbstractSchemaSettingSpec implements MariaDbTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "information_schema"
    }
}
