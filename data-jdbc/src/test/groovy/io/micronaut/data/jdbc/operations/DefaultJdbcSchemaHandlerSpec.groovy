package io.micronaut.data.jdbc.operations

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.query.builder.sql.Dialect
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

class DefaultJdbcSchemaHandlerSpec extends Specification {

    private final DefaultJdbcSchemaHandler handler = new DefaultJdbcSchemaHandler()

    void "renders schema names safely for every JDBC dialect"() {
        given:
        Connection connection = Mock()
        Statement statement = Mock()
        connection.createStatement() >> statement

        when:
        handler.useSchema(connection, dialect, schema)

        then:
        1 * statement.execute(expectedSql)
        1 * statement.close()

        where:
        dialect          | schema                                      | expectedSql
        Dialect.H2       | 'tenant-a'                                  | 'SET SCHEMA `tenant-a`;'
        Dialect.MYSQL    | 'tenant`a'                                  | 'USE `tenant``a`;'
        Dialect.SQL_SERVER | 'tenant]a'                                | 'USE [tenant]]a];'
        Dialect.ORACLE   | 'tenant-a'                                  | 'ALTER SESSION SET CURRENT_SCHEMA="TENANT-A"'
        Dialect.ANSI     | 'tenant_1'                                  | 'SET SCHEMA tenant_1;'
    }

    void "uses JDBC schema API for PostgreSQL"() {
        given:
        Connection connection = Mock()

        when:
        handler.useSchema(connection, Dialect.POSTGRES, 'PUBLIC; CREATE TABLE PWNED(id int); --')

        then:
        1 * connection.setSchema('PUBLIC; CREATE TABLE PWNED(id int); --')
        0 * connection.createStatement()
    }

    void "renders schema names safely when creating schemas"() {
        given:
        Connection connection = Mock()
        Statement statement = Mock()
        connection.createStatement() >> statement

        when:
        handler.createSchema(connection, dialect, 'tenant-a')

        then:
        1 * statement.execute(expectedSql)
        1 * statement.close()

        where:
        dialect          | expectedSql
        Dialect.H2       | 'CREATE SCHEMA `tenant-a`;'
        Dialect.MYSQL    | 'CREATE SCHEMA `tenant-a`;'
        Dialect.POSTGRES | 'CREATE SCHEMA "tenant-a";'
        Dialect.SQL_SERVER | 'CREATE SCHEMA [tenant-a];'
        Dialect.ORACLE   | 'CREATE DATABASE "TENANT-A";'
        Dialect.ANSI     | 'CREATE SCHEMA "tenant-a";'
    }

    void "H2 does not execute an injected schema payload"() {
        given:
        Connection connection = DriverManager.getConnection('jdbc:h2:mem:schema_handler_injection;DB_CLOSE_DELAY=-1')

        when:
        handler.useSchema(connection, Dialect.H2, 'PUBLIC; CREATE TABLE PWNED(id int); --')

        then:
        thrown(DataAccessException)
        connection.createStatement().withCloseable { statement ->
            def result = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PWNED'")
            result.next()
            assert result.getInt(1) == 0
            true
        }

        cleanup:
        connection?.close()
    }

    void "H2 can use a schema requiring identifier quoting"() {
        given:
        Connection connection = DriverManager.getConnection('jdbc:h2:mem:schema_handler_quoted;DB_CLOSE_DELAY=-1')

        when:
        handler.createSchema(connection, Dialect.H2, 'tenant-a')
        handler.useSchema(connection, Dialect.H2, 'tenant-a')
        connection.createStatement().withCloseable { statement ->
            statement.execute('CREATE TABLE marker(id int)')
        }

        then:
        connection.createStatement().withCloseable { statement ->
            def result = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = 'TENANT-A' AND TABLE_NAME = 'MARKER'")
            result.next()
            assert result.getInt(1) == 1
            true
        }

        cleanup:
        connection?.close()
    }

    void "H2 does not execute an injected schema creation payload"() {
        given:
        Connection connection = DriverManager.getConnection('jdbc:h2:mem:schema_handler_create_injection;DB_CLOSE_DELAY=-1')

        when:
        handler.createSchema(connection, Dialect.H2, 'PUBLIC; CREATE TABLE PWNED(id int); --')

        then:
        connection.createStatement().withCloseable { statement ->
            def result = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PWNED'")
            result.next()
            assert result.getInt(1) == 0
            true
        }

        cleanup:
        connection?.close()
    }
}
