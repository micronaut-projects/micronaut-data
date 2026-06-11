package io.micronaut.data.r2dbc.oraclexe.bool.nativebool

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration
import io.micronaut.data.r2dbc.oraclexe.Oracle23TestPropertyProvider
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class R2dbcOracleNativeBooleanTypeSpec extends Specification implements Oracle23TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    ConnectionFactory connectionFactory = applicationContext.getBean(ConnectionFactory)

    void "test oracle native boolean type is used for r2dbc schema and queries"() {
        given:
        assert applicationContext.getBean(DataR2dbcConfiguration)
            .resolveDialectOptions()
            .isAtLeast(SqlDialectOptions.ORACLE_23_1_COMPATIBILITY)
        def repository = applicationContext.getBean(R2dbcNativeOracleBooleanRepository)

        when:
        def trueEntity = repository.save(new R2dbcNativeOracleBooleanEntity(null, true))
        def falseEntity = repository.save(new R2dbcNativeOracleBooleanEntity(null, false))
        def nullEntity = repository.save(new R2dbcNativeOracleBooleanEntity(null, null))

        then:
        repository.findById(trueEntity.id()).orElseThrow().active()
        !repository.findById(falseEntity.id()).orElseThrow().active()
        repository.findById(nullEntity.id()).orElseThrow().active() == null

        and:
        repository.findByActiveTrue()*.id() == [trueEntity.id()]
        repository.findByActiveFalse()*.id() == [falseEntity.id()]

        and:
        columnTypeName("R2DBC_NATIVE_ORACLE_BOOLEAN_ENTITY", "ACTIVE") == "BOOLEAN"
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
