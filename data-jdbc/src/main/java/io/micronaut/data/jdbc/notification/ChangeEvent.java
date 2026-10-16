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

import io.micronaut.core.annotation.Experimental;

import java.util.Optional;

/**
 * A database change notification for a persistent entity type.
 *
 * <p>Entity state is optional. A provider may load it after the change has committed, so it is
 * not necessarily a snapshot of the row at the time of the change. An event that does not identify
 * one row, such as an invalidation, has no single entity associated with it.</p>
 *
 * @param <E> The persistent entity type.
 * @since 5.3.0
 */
@Experimental
public interface ChangeEvent<E> {

    /**
     * Returns the operation reported by the notification provider.
     *
     * @return The reported change operation.
     */
    ChangeOperation operation();

    /**
     * Returns the entity state associated with this event, when it is available.
     *
     * @return The entity, or empty when the provider has no entity state for this event.
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
