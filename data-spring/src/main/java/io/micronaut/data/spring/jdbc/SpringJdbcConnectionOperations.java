/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.spring.jdbc;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.jdbc.operations.DataSourceConnectionOperationsImpl;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
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

/**
 * Spring JDBC connection operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@EachBean(DataSource.class)
@Replaces(DataSourceConnectionOperationsImpl.class)
// TODO: We should avoid using @Replaces, there should be a way to use different data sources with Micronaut and Spring TX
public final class SpringJdbcConnectionOperations implements ConnectionOperations<Connection> {

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
                return Optional.of(createStatus(connectionHandle.getConnection()));
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> R execute(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, R> callback) {
        return new JdbcTemplate(dataSource).execute((ConnectionCallback<R>) connection -> callback.apply(createStatus(connection)));
    }

    private DefaultConnectionStatus<Connection> createStatus(Connection connection) {
        return new DefaultConnectionStatus<>(
            connection,
            ConnectionDefinition.DEFAULT,
            true
        );
    }

}
