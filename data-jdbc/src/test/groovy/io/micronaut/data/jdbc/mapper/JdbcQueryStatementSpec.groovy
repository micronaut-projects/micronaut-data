package io.micronaut.data.jdbc.mapper

import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import spock.lang.Specification

import java.sql.Types

class JdbcQueryStatementSpec extends Specification {

    void "findSqlType returns dialect specific boolean and object mappings"() {
        expect:
        JdbcQueryStatement.findSqlType(DataType.BOOLEAN, Dialect.ORACLE) == Types.BIT
        JdbcQueryStatement.findSqlType(DataType.BOOLEAN, Dialect.POSTGRES) == Types.BOOLEAN
        JdbcQueryStatement.findSqlType(DataType.OBJECT, Dialect.ORACLE) == Types.NULL
        JdbcQueryStatement.findSqlType(DataType.OBJECT, Dialect.POSTGRES) == Types.OTHER
    }
}
