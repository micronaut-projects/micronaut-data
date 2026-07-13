package io.micronaut.data.jdbc.oraclexe.bool.legacy

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.oraclexe.OracleXE21TestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class OracleLegacyBooleanTypeSpec extends Specification implements OracleXE21TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    void "test oracle 21 uses legacy boolean type for schema and queries"() {
        given:
        def repository = applicationContext.getBean(LegacyOracleBooleanRepository)

        when:
        def trueEntity = repository.save(new LegacyOracleBooleanEntity(null, true))
        def falseEntity = repository.save(new LegacyOracleBooleanEntity(null, false))
        def nullEntity = repository.save(new LegacyOracleBooleanEntity(null, null))

        then:
        repository.findById(trueEntity.id()).orElseThrow().active()
        !repository.findById(falseEntity.id()).orElseThrow().active()
        repository.findById(nullEntity.id()).orElseThrow().active() == null

        and:
        repository.findByActiveTrue()*.id() == [trueEntity.id()]
        repository.findByActiveFalse()*.id() == [falseEntity.id()]

        and:
        columnTypeName("LEGACY_ORACLE_BOOLEAN_ENTITY", "ACTIVE") == "NUMBER"
    }

    void "test oracle 21 warns for a native boolean query target and rejects native boolean SQL"() {
        given:
        def legacyRepository = applicationContext.getBean(LegacyOracleBooleanRepository)
        def nativeRepository = applicationContext.getBean(Oracle21NativeBooleanRepository)
        legacyRepository.save(new LegacyOracleBooleanEntity(null, true))

        when:
        nativeRepository.findByActiveTrue()

        then:
        def exception = thrown(DataAccessException)
        exception.message.contains("Error executing SQL Query")
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
