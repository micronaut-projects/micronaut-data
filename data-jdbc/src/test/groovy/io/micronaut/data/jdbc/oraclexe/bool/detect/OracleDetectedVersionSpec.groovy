package io.micronaut.data.jdbc.oraclexe.bool.detect

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class OracleDetectedVersionSpec extends Specification implements OracleTestPropertyProvider {

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
            "datasources.default.dialect-options.detect-version": "true"
        ]
    }

    void "schema generation targets the detected 23ai version"() {
        expect:
        columnTypeName("DETECTED_VERSION_BOOLEAN_ENTITY", "ACTIVE") == "BOOLEAN"
    }

    void "a repository compiled without a target version still reads and writes the native BOOLEAN column"() {
        given:
        def repository = applicationContext.getBean(DetectedVersionBooleanRepository)

        when:
        def active = repository.save(new DetectedVersionBooleanEntity(null, true))
        def inactive = repository.save(new DetectedVersionBooleanEntity(null, false))

        then:
        repository.findById(active.id()).orElseThrow().active()
        !repository.findById(inactive.id()).orElseThrow().active()
        repository.findByActive(true)*.id() == [active.id()]
    }

    private String columnTypeName(String tableName, String columnName) {
        def dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource))
        dataSource.connection.withCloseable { connection ->
            def columns = connection.metaData.getColumns(null, connection.schema, tableName, columnName)
            columns.withCloseable {
                assert columns.next()
                return columns.getString("TYPE_NAME")
            }
        }
    }
}
