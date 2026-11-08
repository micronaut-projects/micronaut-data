package io.micronaut.data.r2dbc.oraclexe.bool.detect

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class R2dbcOracleDetectedVersionSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    ConnectionFactory connectionFactory = applicationContext.getBean(ConnectionFactory)

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
        return OracleXETestPropertyProvider.super.getProperties() + [
            "r2dbc.datasources.default.dialect-options.detect-version": "true"
        ]
    }

    void "schema generation targets the version reported by the R2DBC connection"() {
        expect:
        columnTypeName("R2DBC_DETECTED_VERSION_BOOLEAN_ENTITY", "ACTIVE") == "BOOLEAN"
    }

    void "a repository compiled without a target version still reads and writes the native BOOLEAN column"() {
        given:
        def repository = applicationContext.getBean(R2dbcDetectedVersionBooleanRepository)

        when:
        def active = repository.save(new R2dbcDetectedVersionBooleanEntity(null, true))
        def inactive = repository.save(new R2dbcDetectedVersionBooleanEntity(null, false))

        then:
        repository.findById(active.id()).orElseThrow().active()
        !repository.findById(inactive.id()).orElseThrow().active()
        repository.findByActive(true)*.id() == [active.id()]
    }

    private String columnTypeName(String tableName, String columnName) {
        def query = """
            SELECT DATA_TYPE
            FROM USER_TAB_COLUMNS
            WHERE TABLE_NAME = '${tableName}' AND COLUMN_NAME = '${columnName}'
        """
        return Mono.usingWhen(connectionFactory.create(),
            { Connection connection ->
                Mono.from(connection.createStatement(query).execute())
                    .flatMap { result ->
                        Mono.from(result.map { row, metadata ->
                            row.get("DATA_TYPE", String)
                        })
                    }
            },
            { Connection connection -> connection.close() }
        ).block()
    }
}
