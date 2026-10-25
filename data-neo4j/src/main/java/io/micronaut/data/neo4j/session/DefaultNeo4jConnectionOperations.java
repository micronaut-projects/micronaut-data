/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.neo4j.session;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import org.neo4j.driver.Driver;

/**
 * Opens a Neo4j session per connection.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(Neo4jDriverRegistration.class)
@Internal
final class DefaultNeo4jConnectionOperations extends AbstractConnectionOperations<Neo4jConnection> implements Neo4jConnectionOperations {

    private final Driver driver;
    private final Neo4jSessionConfigurer sessions;

    DefaultNeo4jConnectionOperations(@Parameter Neo4jDriverRegistration registration, @Parameter Neo4jSessionConfigurer sessions) {
        this.driver = registration.driver();
        this.sessions = sessions;
    }

    @Override
    protected Neo4jConnection openConnection(ConnectionDefinition definition) {
        return new Neo4jConnection(driver.session(sessions.sessionConfig()));
    }

    @Override
    protected void setupConnection(ConnectionStatus<Neo4jConnection> connectionStatus) {
        // Sessions need no setup: the database is chosen when the session is opened
    }

    @Override
    protected void closeConnection(ConnectionStatus<Neo4jConnection> connectionStatus) {
        connectionStatus.getConnection().getSession().close();
    }
}
