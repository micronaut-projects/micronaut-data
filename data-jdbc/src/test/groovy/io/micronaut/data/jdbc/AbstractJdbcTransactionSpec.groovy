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
    boolean readOnlyRecorderInstalled

    def setup() {
        if (!readOnlyRecorderInstalled) {
            context.getBean(DefaultDataSourceConnectionOperations).addConnectionCustomizer(new ConnectionCustomizer<Connection>() {
                @Override
                def <R> Function<ConnectionStatus<Connection>, R> intercept(Function<ConnectionStatus<Connection>, R> operation) {
                    return { ConnectionStatus<Connection> status ->
                        readOnlyDuringOperation.add(status.getConnection().isReadOnly())
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
    }

    private BookRepository getRepository() {
        return context.getBean(getBookRepositoryClass())
    }

    private DataJdbcConfiguration getJdbcConfiguration() {
        return context.getBean(DataJdbcConfiguration, Qualifiers.byName("default"))
    }

    private boolean isConnectionReadOnly() {
        return getConnectionOperations().executeWrite { status -> status.getConnection().isReadOnly() }
    }

    void "test a read outside of a transaction doesn't toggle the read-only flag by default"() {
        given:
            readOnlyDuringOperation.clear()
        when:
            repository.count()
        then:
            readOnlyDuringOperation == [false]
            !isConnectionReadOnly()
    }

    void "test a read outside of a transaction toggles the read-only flag when enabled"() {
        given:
            jdbcConfiguration.readOnlyConnectionPerOperation = true
            readOnlyDuringOperation.clear()
        when:
            repository.count()
        then:
            readOnlyDuringOperation == [supportsReadOnlyFlag()]
        and: "The flag is restored before the connection is returned"
            !isConnectionReadOnly()
        cleanup:
            jdbcConfiguration.readOnlyConnectionPerOperation = DataJdbcConfiguration.DEFAULT_READ_ONLY_CONNECTION_PER_OPERATION
    }

    void "test reads in an explicit read-only connection stay read-only"() {
        when:
            def flags = getConnectionOperations().executeRead { status ->
                def outer = status.getConnection().isReadOnly()
                repository.count()
                repository.findAll()
                [outer, status.getConnection().isReadOnly()]
            }
        then:
            flags == [supportsReadOnlyFlag(), supportsReadOnlyFlag()]
            readOnlyDuringOperation == [supportsReadOnlyFlag()] * 3
        and:
            !isConnectionReadOnly()
    }

    void "test a writable connection is writable again after an inner read-only transaction"() {
        when:
            def flag = getConnectionOperations().executeWrite { status ->
                getTransactionOperations().executeRead { repository.count() }
                repository.count()
                status.getConnection().isReadOnly()
            }
        then:
            !flag
            !isConnectionReadOnly()
    }

    void "test reads in a read-only transaction are read-only"() {
        when:
            def flag = getTransactionOperations().executeRead { status ->
                repository.count()
                status.getConnection().isReadOnly()
            }
        then:
            flag == supportsReadOnlyFlag()
            readOnlyDuringOperation.every { it == supportsReadOnlyFlag() }
        and:
            !isConnectionReadOnly()
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
