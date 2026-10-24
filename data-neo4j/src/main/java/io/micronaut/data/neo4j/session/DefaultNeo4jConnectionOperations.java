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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

/**
 * Opens a Neo4j session per connection.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Requires(beans = Driver.class)
@Internal
final class DefaultNeo4jConnectionOperations extends AbstractConnectionOperations<Neo4jConnection> implements Neo4jConnectionOperations {

    private final Driver driver;
    private final @Nullable String database;

    DefaultNeo4jConnectionOperations(Driver driver,
                                     @Value("${micronaut.data.neo4j.database:}") @Nullable String database) {
        this.driver = driver;
        this.database = database == null || database.isBlank() ? null : database;
    }

    @Override
    protected Neo4jConnection openConnection(ConnectionDefinition definition) {
        SessionConfig.Builder builder = SessionConfig.builder();
        if (database != null) {
            builder.withDatabase(database);
        }
        return new Neo4jConnection(driver.session(builder.build()));
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
