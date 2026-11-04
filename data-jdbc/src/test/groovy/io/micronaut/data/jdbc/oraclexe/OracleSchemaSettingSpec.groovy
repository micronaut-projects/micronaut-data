package io.micronaut.data.jdbc.oraclexe

import io.micronaut.data.jdbc.AbstractSchemaSettingSpec
import io.micronaut.data.runtime.config.SchemaGenerate

class OracleSchemaSettingSpec extends AbstractSchemaSettingSpec implements OracleTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    String otherSchema() {
        return "SYS"
    }
}
