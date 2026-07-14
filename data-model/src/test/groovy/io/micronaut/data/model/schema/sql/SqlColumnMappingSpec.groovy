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
        column.getSqlType(Dialect.ORACLE, SqlDialectOptions.of(Dialect.ORACLE, SqlDialectOptions.ORACLE_23_1_VERSION)) == "BOOLEAN"
    }

    void "dialect options must match the requested dialect"() {
        given:
        def column = new SqlColumnMapping("active", DataType.BOOLEAN, SqlDbType.BOOLEAN)

        when:
        column.getSqlType(Dialect.ORACLE, SqlDialectOptions.defaults(Dialect.POSTGRES))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Dialect options must match the requested dialect"
    }

    void "dialect options compare parsed target versions"() {
        expect:
        SqlDialectOptions.of(Dialect.ORACLE, version).isVersionAtLeast(SqlDialectOptions.ORACLE_23_1_VERSION) == compatible

        where:
        version      | compatible
        "23.1"      | true
        "23.1.0"    | true
        "23.4"      | true
        "24.0"      | true
        "23"        | false
        "23.0"      | false
        "23.1.0.1" | false
        "23_1"      | false
        "23.foo"    | false
        "ORACLE_FOO" | false
        null         | false
    }
}
