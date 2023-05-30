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
package io.micronaut.data.connection.jdbc;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.manager.ConnectionDefinition;

import java.time.Duration;
import java.util.Optional;

final class DefaultJdbcConnectionDefinition implements JdbcConnectionDefinition {

    private final ConnectionDefinition connectionDefinition;
    @Nullable
    private final Boolean autoCommit;

    public DefaultJdbcConnectionDefinition(ConnectionDefinition connectionDefinition, @Nullable Boolean autoCommit) {
        this.connectionDefinition = connectionDefinition;
        this.autoCommit = autoCommit;
    }

    @Override
    public Optional<Boolean> autoCommit() {
        return Optional.ofNullable(autoCommit);
    }

    @NonNull
    @Override
    public JdbcConnectionDefinition readOnly() {
        return new DefaultJdbcConnectionDefinition(connectionDefinition.readOnly(), autoCommit);
    }

    @Override
    public JdbcConnectionDefinition withAutoCommit(boolean autoCommit) {
        return new DefaultJdbcConnectionDefinition(connectionDefinition, autoCommit);
    }

    @Override
    public Propagation getPropagationBehavior() {
        return connectionDefinition.getPropagationBehavior();
    }

    @Override
    public Optional<TransactionIsolation> getIsolationLevel() {
        return connectionDefinition.getIsolationLevel();
    }

    @Override
    public Optional<Duration> getTimeout() {
        return connectionDefinition.getTimeout();
    }

    @Override
    public Optional<Boolean> isReadOnly() {
        return connectionDefinition.isReadOnly();
    }

    @Override
    public String getName() {
        return connectionDefinition.getName();
    }

    @Override
    public JdbcConnectionDefinition withPropagation(Propagation propagation) {
        return new DefaultJdbcConnectionDefinition(connectionDefinition.withPropagation(propagation), autoCommit);
    }

    @Override
    public ConnectionDefinition withName(String name) {
        return new DefaultJdbcConnectionDefinition(connectionDefinition.withName(name), autoCommit);
    }
}
