package io.micronaut.data.r2dbc.oraclexe.readonly

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Result
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleR2dbcReadOnlyTransactionSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    R2dbcReactorTransactionOperations transactionOperations = context.getBean(R2dbcReactorTransactionOperations)

    @Override
    List<String> packages() {
        // no entities: the spec creates its own table
        return [getClass().package.name + ".none"]
    }

    void setupSpec() {
        execute(TransactionDefinition.DEFAULT, "BEGIN EXECUTE IMMEDIATE 'DROP TABLE READ_ONLY_TX'; EXCEPTION WHEN OTHERS THEN NULL; END;")
        execute(TransactionDefinition.DEFAULT, "CREATE TABLE READ_ONLY_TX (ID NUMBER(10) PRIMARY KEY)")
    }

    void "a read-only transaction rejects writes"() {
        when:
        execute(readOnly(), "INSERT INTO READ_ONLY_TX (ID) VALUES (1)")

        then: "Oracle rejects DML in a READ ONLY transaction"
        Exception e = thrown()
        (e.message + (e.cause?.message ?: "")).contains("ORA-01456")
    }

    void "a read-only transaction reads, and a read-write transaction still writes"() {
        when:
        // reads DUAL: a READ ONLY transaction cannot read a table created moments before it (ORA-01466)
        Long readOnlyCount = count(readOnly(), "SELECT COUNT(*) FROM DUAL")
        execute(TransactionDefinition.DEFAULT, "INSERT INTO READ_ONLY_TX (ID) VALUES (2)")
        Long written = count(TransactionDefinition.DEFAULT, "SELECT COUNT(*) FROM READ_ONLY_TX WHERE ID = 2")

        then:
        readOnlyCount == 1
        written == 1
    }

    private Long count(TransactionDefinition definition, String sql) {
        return transactionOperations.withTransactionMono(definition, status ->
            Mono.from(status.connection.createStatement(sql).execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long))))
        ).block()
    }

    private static TransactionDefinition readOnly() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        definition.readOnly = true
        return definition
    }

    private void execute(TransactionDefinition definition, String sql) {
        transactionOperations.withTransactionMono(definition, status ->
            Flux.from(status.connection.createStatement(sql).execute())
                .flatMap(Result::getRowsUpdated)
                .then()
        ).block()
    }
}
