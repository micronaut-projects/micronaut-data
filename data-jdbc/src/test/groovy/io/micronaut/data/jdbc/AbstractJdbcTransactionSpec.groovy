package io.micronaut.data.jdbc

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.support.ConnectionCustomizer
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Shared
import io.micronaut.data.connection.jdbc.operations.DataSourceConnectionOperations
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations
import io.micronaut.data.tck.tests.AbstractTransactionSpec
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.jdbc.DataSourceTransactionManager

import java.sql.Connection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function

abstract class AbstractJdbcTransactionSpec extends AbstractTransactionSpec {

    @Shared
    List<Boolean> readOnlyDuringOperation = new CopyOnWriteArrayList<>()

    @Shared
    List<Connection> physicalConnections = new CopyOnWriteArrayList<>()

    @Shared
    volatile boolean recordReadOnly

    @Shared
    boolean readOnlyRecorderInstalled

    def setup() {
        if (!readOnlyRecorderInstalled) {
            // The customizer cannot be removed; it only records while a read-only feature runs
            context.getBean(DefaultDataSourceConnectionOperations).addConnectionCustomizer(new ConnectionCustomizer<Connection>() {
                @Override
                def <R> Function<ConnectionStatus<Connection>, R> intercept(Function<ConnectionStatus<Connection>, R> operation) {
                    return { ConnectionStatus<Connection> status ->
                        if (recordReadOnly) {
                            Connection connection = status.getConnection()
                            readOnlyDuringOperation.add(connection.isReadOnly())
                            physicalConnections.add(connection.unwrap(Connection))
                        }
                        return operation.apply(status)
                    } as Function<ConnectionStatus<Connection>, R>
                }

                @Override
                int getOrder() {
                    return 0
                }
            })
            readOnlyRecorderInstalled = true
        }
        readOnlyDuringOperation.clear()
        physicalConnections.clear()
    }

    def cleanup() {
        recordReadOnly = false
    }

    /**
     * Whether the driver reports the read-only flag back from {@link Connection#isReadOnly()}.
     *
     * @return true if it does
     */
    boolean reportsReadOnlyFlag() {
        return supportsReadOnlyFlag()
    }

    private BookRepository getRepository() {
        return context.getBean(getBookRepositoryClass())
    }

    private DataJdbcConfiguration getJdbcConfiguration() {
        return context.getBean(DataJdbcConfiguration, Qualifiers.byName("default"))
    }

    /**
     * Whether a connection used by the recorded operations is still read-only after it was returned to the pool.
     */
    private boolean anyUsedConnectionReadOnly() {
        assert !physicalConnections.isEmpty()
        return physicalConnections.any { it.isReadOnly() }
    }

    void "test a read outside of a transaction doesn't toggle the read-only flag by default"() {
        given:
            recordReadOnly = true
        when:
            repository.count()
        then:
            readOnlyDuringOperation == [false]
            !anyUsedConnectionReadOnly()
    }

    void "test a read outside of a transaction toggles the read-only flag when enabled"() {
        given:
            // Features run sequentially, the shared configuration is restored in the cleanup block
            jdbcConfiguration.readOnlyConnectionPerOperation = true
            recordReadOnly = true
        when:
            repository.count()
        then:
            readOnlyDuringOperation == [reportsReadOnlyFlag()]
        and: "The flag is restored before the connection is returned"
            !anyUsedConnectionReadOnly()
        cleanup:
            jdbcConfiguration.readOnlyConnectionPerOperation = DataJdbcConfiguration.DEFAULT_READ_ONLY_CONNECTION_PER_OPERATION
    }

    void "test reads in an explicit read-only connection stay read-only"() {
        given:
            recordReadOnly = true
        when:
            def flags = getConnectionOperations().executeRead { status ->
                def outer = status.getConnection().isReadOnly()
                repository.count()
                repository.findAll()
                [outer, status.getConnection().isReadOnly()]
            }
        then:
            flags == [reportsReadOnlyFlag(), reportsReadOnlyFlag()]
            readOnlyDuringOperation == [reportsReadOnlyFlag()] * 3
        and:
            !anyUsedConnectionReadOnly()
    }

    void "test reads in a read-only transaction are read-only"() {
        given:
            recordReadOnly = true
        when:
            def flag = getTransactionOperations().executeRead { status ->
                repository.count()
                status.getConnection().isReadOnly()
            }
        then:
            flag == reportsReadOnlyFlag()
            readOnlyDuringOperation.every { it == reportsReadOnlyFlag() }
        and:
            !anyUsedConnectionReadOnly()
    }

    @Override
    protected TransactionOperations getTransactionOperations() {
        return context.getBean(DataSourceTransactionManager)
    }

    @Override
    protected ConnectionOperations getConnectionOperations() {
        return context.getBean(DefaultDataSourceConnectionOperations)
    }

    @Override
    protected Runnable getNoTxCheck() {
        DefaultDataSourceConnectionOperations connectionOperations = context.getBean(DefaultDataSourceConnectionOperations)
        return new Runnable() {
            @Override
            void run() {
                def status = connectionOperations.findConnectionStatus()
                if (status.isEmpty()) {
                    return
                }
                Connection connection = status.get().getConnection()
                // No transaction -> autoCommit == true
                assert connection.getAutoCommit()
            }
        }
    }

}
