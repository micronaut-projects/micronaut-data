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
package io.micronaut.data.hibernate.connection;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import io.micronaut.data.hibernate.conf.RequiresSyncHibernate;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.Connection;
import java.util.Optional;
import java.util.function.Function;

/**
 * The connection operations that extract {@link Connection} from Hibernate Session.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@RequiresSyncHibernate
@EachBean(HibernateConnectionOperations.class)
@Replaces(DefaultDataSourceConnectionOperations.class)
public final class HibernateConnectionConnectionOperations implements ConnectionOperations<Connection> {

    private final HibernateConnectionOperations hibernateConnectionOperations;

    public HibernateConnectionConnectionOperations(HibernateConnectionOperations hibernateConnectionOperations) {
        this.hibernateConnectionOperations = hibernateConnectionOperations;
    }

    @Override
    public Optional<ConnectionStatus<Connection>> findConnectionStatus() {
        return hibernateConnectionOperations.findConnectionStatus().map(this::createConnectionStatus);
    }

    @Override
    public <R> R execute(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, R> callback) {
        return hibernateConnectionOperations.execute(definition, connectionStatus -> callback.apply(createConnectionStatus(connectionStatus)));
    }

    private ConnectionStatus<Connection> createConnectionStatus(ConnectionStatus<Session> connectionStatus) {
        return new DefaultConnectionStatus<>(
            getConnection(connectionStatus.getConnection()),
            connectionStatus.getDefinition(),
            connectionStatus.isNew()
        );
    }

    private Connection getConnection(Session session) {
        return ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
    }
}
