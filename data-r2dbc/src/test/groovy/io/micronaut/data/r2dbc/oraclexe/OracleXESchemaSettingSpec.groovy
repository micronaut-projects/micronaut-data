package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.r2dbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class OracleXESchemaSettingSpec extends AbstractSchemaSettingSpec implements OracleXETestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "SYS"
    }
}
