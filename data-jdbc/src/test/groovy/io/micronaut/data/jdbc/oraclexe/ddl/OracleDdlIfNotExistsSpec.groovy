package io.micronaut.data.jdbc.oraclexe.ddl

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class OracleDdlIfNotExistsSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return OracleTestPropertyProvider.super.getProperties() + [
            "datasources.default.dialect-options.version": "23.26"
        ]
    }

    void "generated UUID identifiers default to a UUID string"() {
        when:
        String id = withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeUpdate("INSERT INTO \"ORACLE_UUID_DDL_ENTITY\" (\"NAME\") VALUES ('a')")
                statement.executeQuery("SELECT \"ID\" FROM \"ORACLE_UUID_DDL_ENTITY\" WHERE \"NAME\" = 'a'").withCloseable { resultSet ->
                    assert resultSet.next()
                    return resultSet.getString(1)
                }
            }
        }

        then:
        id ==~ /[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/
        UUID.fromString(id).toString() == id
    }

    void "generating the schema again does not fail"() {
        given:
        PersistentEntity entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(OracleUuidDdlEntity)
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE, "23.26")
        String[] statements = builder.buildCreateTableStatements(entity)

        expect:
        statements.every { it.contains(' IF NOT EXISTS ') }

        when:
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statements.each { statement.execute(it) }
            }
        }

        then:
        noExceptionThrown()
    }

    private <T> T withConnection(Closure<T> work) {
        DataSource dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource))
        return dataSource.connection.withCloseable(work)
    }
}
