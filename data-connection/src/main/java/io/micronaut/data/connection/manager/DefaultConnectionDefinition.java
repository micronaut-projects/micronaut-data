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
package io.micronaut.data.connection.manager;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * Default implementation of the {@link ConnectionDefinition} interface,
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public record DefaultConnectionDefinition(
    @Nullable String name,
    Propagation propagationBehavior,
    @Nullable TransactionIsolation isolationLevel,
    @Nullable Duration timeout,
    @Nullable Boolean readOnlyDef
) implements ConnectionDefinition {

    DefaultConnectionDefinition(String name) {
        this(name, PROPAGATION_DEFAULT, null, null, null);
    }

    DefaultConnectionDefinition(String name, Boolean readOnly) {
        this(name, PROPAGATION_DEFAULT, null, null, readOnly);
    }

    public DefaultConnectionDefinition(Propagation propagationBehaviour) {
        this(null, propagationBehaviour, null, null, null);
    }

    @NonNull
    @Override
    public Propagation getPropagationBehavior() {
        return propagationBehavior;
    }

    @NonNull
    @Override
    public Optional<TransactionIsolation> getIsolationLevel() {
        return Optional.ofNullable(isolationLevel);
    }

    @NonNull
    @Override
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public Optional<Boolean> isReadOnly() {
        return Optional.ofNullable(readOnlyDef);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ConnectionDefinition withPropagation(Propagation propagation) {
        return new DefaultConnectionDefinition(name, propagation, isolationLevel, timeout, readOnlyDef);
    }

    @Override
    public ConnectionDefinition withName(String name) {
        return new DefaultConnectionDefinition(name, propagationBehavior, isolationLevel, timeout, readOnlyDef);
    }

    @Override
    public ConnectionDefinition readOnly() {
        return new DefaultConnectionDefinition(name, propagationBehavior, isolationLevel, timeout, true);
    }
}

