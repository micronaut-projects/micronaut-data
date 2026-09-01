package io.micronaut.data.r2dbc.operations

import io.micronaut.data.model.query.builder.sql.Dialect
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.function.BiFunction

class DefaultR2dbcSchemaHandlerSpec extends Specification {

    private final DefaultR2dbcSchemaHandler handler = new DefaultR2dbcSchemaHandler()

    void "renders schema names safely for every supported R2DBC dialect"() {
        given:
        Connection connection = Mock()
        Statement statement = Mock()
        Result result = Mock()
        connection.createStatement(_) >> statement
        statement.execute() >> Flux.just(result)
        result.getRowsUpdated() >> Flux.just(0)

        when:
        Mono.from(handler.useSchema(connection, dialect, schema)).block()

        then:
        1 * connection.createStatement(expectedSql) >> statement
        1 * statement.execute() >> Flux.just(result)
        1 * result.getRowsUpdated() >> Flux.just(0)

        where:
        dialect            | schema                                   | expectedSql
        Dialect.H2         | 'tenant-a'                               | 'SET SCHEMA `tenant-a`;'
        Dialect.MYSQL      | 'tenant`a'                               | 'USE `tenant``a`;'
        Dialect.POSTGRES   | 'tenant_1'                               | "SET SCHEMA 'tenant_1';"
        Dialect.POSTGRES   | 'tenant-a'                               | 'SET search_path TO "tenant-a";'
        Dialect.SQL_SERVER | 'tenant]a'                               | 'USE [tenant]]a];'
        Dialect.ORACLE     | 'tenant-a'                               | 'ALTER SESSION SET CURRENT_SCHEMA="TENANT-A"'
    }

    void "renders the injection payload as one identifier"() {
        given:
        Connection connection = Mock()
        Statement statement = Mock()
        Result result = Mock()
        connection.createStatement('SET SCHEMA `PUBLIC; CREATE TABLE PWNED(id int); --`;') >> statement
        statement.execute() >> Flux.just(result)
        result.getRowsUpdated() >> Flux.just(0)

        when:
        Mono.from(handler.useSchema(connection, Dialect.H2, 'PUBLIC; CREATE TABLE PWNED(id int); --')).block()

        then:
        1 * connection.createStatement('SET SCHEMA `PUBLIC; CREATE TABLE PWNED(id int); --`;') >> statement
        1 * statement.execute() >> Flux.just(result)
        1 * result.getRowsUpdated() >> Flux.just(0)
    }

    void "renders schema names safely when creating schemas"() {
        given:
        Connection connection = Mock()
        Statement statement = Mock()
        Result result = Mock()
        connection.createStatement(_) >> statement
        statement.execute() >> Flux.just(result)
        result.getRowsUpdated() >> Flux.just(0)

        when:
        Mono.from(handler.createSchema(connection, dialect, 'tenant-a')).block()

        then:
        1 * connection.createStatement(expectedSql) >> statement
        1 * statement.execute() >> Flux.just(result)
        1 * result.getRowsUpdated() >> Flux.just(0)

        where:
        dialect            | expectedSql
        Dialect.H2         | 'CREATE SCHEMA `tenant-a`;'
        Dialect.MYSQL      | 'CREATE SCHEMA `tenant-a`;'
        Dialect.POSTGRES   | 'CREATE SCHEMA "tenant-a";'
        Dialect.SQL_SERVER | 'CREATE SCHEMA [tenant-a];'
        Dialect.ORACLE     | 'CREATE DATABASE "TENANT-A";'
    }

    void "H2 does not execute an injected schema payload"() {
        given:
        ConnectionFactory connectionFactory = ConnectionFactories.get('r2dbc:h2:mem:///schema_handler_injection;DB_CLOSE_DELAY=10')
        Connection connection = Mono.from(connectionFactory.create()).block()
        Throwable error

        when:
        try {
            Mono.from(handler.useSchema(connection, Dialect.H2, 'PUBLIC; CREATE TABLE PWNED(id int); --')).block()
        } catch (Throwable throwable) {
            error = throwable
        }

        then:
        error != null
        def count = Mono.from(connection.createStatement("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PWNED'").execute())
            .flatMap { result -> Mono.from(result.map({ row, metadata -> 1L } as BiFunction)).defaultIfEmpty(0L) }
            .block()
        count == 0L

        cleanup:
        if (connection != null) {
            Mono.from(connection.close()).block()
        }
    }

    void "H2 can use a schema requiring identifier quoting"() {
        given:
        ConnectionFactory connectionFactory = ConnectionFactories.get('r2dbc:h2:mem:///schema_handler_quoted;DB_CLOSE_DELAY=10')
        Connection connection = Mono.from(connectionFactory.create()).block()

        when:
        Mono.from(handler.createSchema(connection, Dialect.H2, 'tenant-a')).block()
        Mono.from(handler.useSchema(connection, Dialect.H2, 'tenant-a')).block()
        Mono.from(connection.createStatement('CREATE TABLE marker(id int)').execute()).block()

        then:
        def count = Mono.from(connection.createStatement("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = 'TENANT-A' AND TABLE_NAME = 'MARKER'").execute())
            .flatMap { result -> Mono.from(result.map({ row, metadata -> 1L } as BiFunction)).defaultIfEmpty(0L) }
            .block()
        count == 1L

        cleanup:
        if (connection != null) {
            Mono.from(connection.close()).block()
        }
    }
}
