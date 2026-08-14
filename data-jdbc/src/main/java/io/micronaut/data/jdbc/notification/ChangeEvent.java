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

import java.util.Optional;

/**
 * A database change notification for a persistent entity type.
 *
 * <p>Entity state is optional. When present, it is the state reloaded while processing the
 * notification and is not guaranteed to be a historical snapshot from the exact change instant.
 * For example, a deleted row cannot be reloaded.</p>
 *
 * @param <E> The persistent entity type.
 * @since 5.2.0
 */
public interface ChangeEvent<E> {

    /**
     * @return The reported change operation.
     */
    ChangeOperation operation();

    /**
     * @return The reloaded entity, or empty when no entity state is available.
     */
    Optional<E> entity();

    /**
     * Finds provider-specific metadata of the requested type.
     *
     * @param metadataType The metadata type.
     * @param <M> The metadata type.
     * @return The metadata when this event contains the requested type.
     */
    <M extends ChangeEventMetadata> Optional<M> metadata(Class<M> metadataType);
}
