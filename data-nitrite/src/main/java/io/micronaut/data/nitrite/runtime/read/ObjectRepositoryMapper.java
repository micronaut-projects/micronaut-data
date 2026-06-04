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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.collection.Document;

/**
 * Centralized entity and DTO mapping for Nitrite operations.
 * Handles both full entity loading and DTO projections using
 * Micronaut's introspection-based entity mapper.
 *
 * @since 5.0.0
 */
@Internal
public final class ObjectRepositoryMapper {

    private final NitriteEntityMapper entityMapper;

    /**
     * Creates a new ObjectRepositoryMapper.
     *
     * @param entityMapper the entity mapper
     */
    public ObjectRepositoryMapper(NitriteEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
    }

    /**
     * Load a full entity from a document.
     * Used when the result type equals the root entity type (no projection).
     *
     * @param doc the document
     * @param entityType the entity type
     * @param <T> the entity type
     * @return the loaded entity, or null if document is null
     */
    public <T> T loadEntity(Document doc, Class<T> entityType) {
        if (doc == null) {
            return null;
        }
        return entityMapper.fromDocument(doc, entityType);
    }
}
