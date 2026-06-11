package io.micronaut.data.jdbc.oraclexe.bool.nativebool

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class OracleNativeBooleanTypeSpec extends Specification implements Oracle23TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    void "test oracle native boolean type is used for schema and queries"() {
        given:
        assert applicationContext.getBean(DataJdbcConfiguration)
            .resolveDialectOptions()
            .isAtLeast(SqlDialectOptions.ORACLE_23_1_COMPATIBILITY)
        def repository = applicationContext.getBean(NativeOracleBooleanRepository)

        when:
        def trueEntity = repository.save(new NativeOracleBooleanEntity(null, true))
        def falseEntity = repository.save(new NativeOracleBooleanEntity(null, false))
        def nullEntity = repository.save(new NativeOracleBooleanEntity(null, null))

        then:
        repository.findById(trueEntity.id()).orElseThrow().active()
        !repository.findById(falseEntity.id()).orElseThrow().active()
        repository.findById(nullEntity.id()).orElseThrow().active() == null

        and:
        repository.findByActiveTrue()*.id() == [trueEntity.id()]
        repository.findByActiveFalse()*.id() == [falseEntity.id()]

        and:
        columnTypeName("NATIVE_ORACLE_BOOLEAN_ENTITY", "ACTIVE") == "BOOLEAN"
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
