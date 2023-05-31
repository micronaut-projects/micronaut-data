package io.micronaut.data.spring.jdbc;

import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHandle;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;
import java.util.function.Function;

public class SpringJdbcConnectionOperations implements ConnectionOperations<Connection> {

    private final DataSource dataSource;

    public SpringJdbcConnectionOperations(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ConnectionStatus<Connection>> findConnectionStatus() {
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        if (conHolder != null) {
            ConnectionHandle connectionHandle = conHolder.getConnectionHandle();
            if (connectionHandle != null) {
                return Optional.of(new DefaultConnectionStatus<>(
                    connectionHandle.getConnection(),
                    ConnectionDefinition.DEFAULT,
                    true
                ));
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> R execute(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, R> callback) {
        return new JdbcTemplate(dataSource).execute((ConnectionCallback<R>) connection -> callback.apply(new DefaultConnectionStatus<>(
            connection,
            ConnectionDefinition.DEFAULT,
            true
        )));
    }

}
