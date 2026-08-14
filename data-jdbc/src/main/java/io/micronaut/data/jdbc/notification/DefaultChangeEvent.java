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
package io.micronaut.data.jdbc.notification;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Default immutable change event used by database notification providers.
 *
 * @param <E> The persistent entity type.
 */
@Internal
public final class DefaultChangeEvent<E> implements ChangeEvent<E> {
    private final ChangeOperation operation;
    private final @Nullable E entity;
    private final @Nullable ChangeEventMetadata metadata;

    public DefaultChangeEvent(ChangeOperation operation,
                              @Nullable E entity,
                              @Nullable ChangeEventMetadata metadata) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.entity = entity;
        this.metadata = metadata;
    }

    @Override
    public ChangeOperation operation() {
        return operation;
    }

    @Override
    public Optional<E> entity() {
        return Optional.ofNullable(entity);
    }

    @Override
    public <M extends ChangeEventMetadata> Optional<M> metadata(Class<M> metadataType) {
        Objects.requireNonNull(metadataType, "metadataType");
        return metadataType.isInstance(metadata) ? Optional.of(metadataType.cast(metadata)) : Optional.empty();
    }
}
