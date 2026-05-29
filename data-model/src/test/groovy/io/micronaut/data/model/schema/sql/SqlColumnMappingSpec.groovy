package io.micronaut.data.model.schema.sql

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import spock.lang.Specification

class SqlColumnMappingSpec extends Specification {

    def "json object SQL type follows dialect JSON mapping"() {
        expect:
        new SqlColumnMapping("extras", DataType.OBJECT, SqlDbType.JSON_OBJECT, false, null, false, false, GeneratedValue.Type.AUTO, null)
                .getSqlType(dialect) == sqlType

        where:
        dialect            | sqlType
        Dialect.ORACLE     | "JSON(OBJECT)"
        Dialect.POSTGRES   | "JSONB"
        Dialect.SQL_SERVER | "NVARCHAR(MAX)"
        Dialect.H2         | "JSON"
    }
}
