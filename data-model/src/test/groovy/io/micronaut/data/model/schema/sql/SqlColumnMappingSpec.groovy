package io.micronaut.data.model.schema.sql

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
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

    void "oracle boolean SQL type follows dialect options"() {
        given:
        def column = new SqlColumnMapping("active", DataType.BOOLEAN, SqlDbType.BOOLEAN)

        expect:
        column.getSqlType(Dialect.ORACLE) == "NUMBER(1)"
        column.getSqlType(Dialect.ORACLE, SqlDialectOptions.of(Dialect.ORACLE, SqlDialectOptions.ORACLE_23_COMPATIBILITY)) == "BOOLEAN"
    }

    void "dialect options normalize compatibility"() {
        expect:
        SqlDialectOptions.of(Dialect.ORACLE, "oracle-23").hasCompatibility(SqlDialectOptions.ORACLE_23_COMPATIBILITY)
        !SqlDialectOptions.defaults(Dialect.ORACLE).hasCompatibility(SqlDialectOptions.ORACLE_23_COMPATIBILITY)
    }
}
