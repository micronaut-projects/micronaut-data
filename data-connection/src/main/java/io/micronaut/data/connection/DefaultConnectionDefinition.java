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
package io.micronaut.data.connection;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.support.ConnectionClientInformation;

import java.time.Duration;
import java.util.Optional;

/**
 * Default implementation of the {@link ConnectionDefinition} interface.
 *
 * @param name                         The connection name
 * @param propagationBehavior          The propagation behaviour
 * @param timeout                      The timeout
 * @param readOnlyValue                The read only
 * @param connectionClientInformation  The connection client information
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public record DefaultConnectionDefinition(
    @Nullable String name,
    Propagation propagationBehavior,
    @Nullable Duration timeout,
    Boolean readOnlyValue,

    @Nullable ConnectionClientInformation connectionClientInformation
) implements ConnectionDefinition {

    DefaultConnectionDefinition(String name) {
        this(name, PROPAGATION_DEFAULT, null, null, null);
    }

    public DefaultConnectionDefinition(Propagation propagationBehaviour) {
        this(null, propagationBehaviour, null, null, null);
    }

    public DefaultConnectionDefinition(String name, boolean readOnly) {
        this(name, PROPAGATION_DEFAULT, null, readOnly, null);
    }

    @Override
    public Optional<Boolean> isReadOnly() {
        return Optional.ofNullable(readOnlyValue);
    }

    @NonNull
    @Override
    public Propagation getPropagationBehavior() {
        return propagationBehavior;
    }

    @NonNull
    @Override
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ConnectionDefinition withPropagation(Propagation propagation) {
        return new DefaultConnectionDefinition(name, propagation, timeout, readOnlyValue, connectionClientInformation);
    }

    @Override
    public ConnectionDefinition withName(String name) {
        return new DefaultConnectionDefinition(name, propagationBehavior, timeout, readOnlyValue, connectionClientInformation);
    }

}

