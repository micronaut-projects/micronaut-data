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
import java.util.function.Supplier;

/**
 * Internal change event whose entity state is materialized inside the listener AOP invocation.
 *
 * <p>A failed materialization is not cached, allowing outer retry advice to invoke the loader
 * again. A successful result, including a {@code null} result, is cached so a later listener
 * failure can be retried without reloading the entity.</p>
 *
 * @param <E> The persistent entity type.
 */
@Internal
public final class DeferredChangeEvent<E> implements ChangeEvent<E> {
    private final ChangeOperation operation;
    private final @Nullable ChangeEventMetadata metadata;
    private final Supplier<@Nullable E> entityLoader;
    private @Nullable E entity;
    private boolean materialized;

    /**
     * Creates a deferred event.
     *
     * @param operation The reported operation.
     * @param metadata Provider-specific event metadata.
     * @param entityLoader The entity loader invoked by the listener interceptor.
     */
    public DeferredChangeEvent(ChangeOperation operation,
                               @Nullable ChangeEventMetadata metadata,
                               Supplier<@Nullable E> entityLoader) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.metadata = metadata;
        this.entityLoader = Objects.requireNonNull(entityLoader, "entityLoader");
    }

    synchronized void materialize() {
        if (materialized) {
            return;
        }
        E loadedEntity = entityLoader.get();
        entity = loadedEntity;
        materialized = true;
    }

    @Override
    public ChangeOperation operation() {
        return operation;
    }

    @Override
    public synchronized Optional<E> entity() {
        return materialized ? Optional.ofNullable(entity) : Optional.empty();
    }

    @Override
    public <M extends ChangeEventMetadata> Optional<M> metadata(Class<M> metadataType) {
        Objects.requireNonNull(metadataType, "metadataType");
        return metadataType.isInstance(metadata) ? Optional.of(metadataType.cast(metadata)) : Optional.empty();
    }
}
