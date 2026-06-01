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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.collection.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Writer for converting entities/DTOs to Nitrite Documents.
 * Used for single entity write operations (insert, update).
 * Handles version properties and entity preparation.
 *
 * @param <T> The entity type
 * @since 5.0.0
 */
@Internal
public final class ObjectRepositoryWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectRepositoryWriter.class);

    private final NitriteEntityMapper entityMapper;
    private final RuntimePersistentProperty<T> versionProperty;

    /**
     * Creates a new ObjectRepositoryWriter.
     *
     * @param entityMapper the entity mapper
     * @param persistentEntity the persistent entity
     */
    public ObjectRepositoryWriter(NitriteEntityMapper entityMapper, RuntimePersistentEntity<T> persistentEntity) {
        this.entityMapper = entityMapper;
        RuntimePersistentProperty<T> version = null;
        try {
            version = persistentEntity.getVersion();
        } catch (IllegalStateException e) {
            LOG.debug("Entity {} has no version property: {}", persistentEntity.getName(), e.getMessage());
        }
        this.versionProperty = version;
    }

    /**
     * Convert an entity to a Document for writing.
     *
     * @param entity the entity
     * @return the Document
     */
    public Document toDocument(T entity) {
        if (entity == null) {
            return null;
        }
        return entityMapper.toDocument(entity);
    }

    /**
     * Check if entity needs version initialization for insert.
     * Returns true if version should be initialized to 0.
     *
     * @param entity the entity
     * @return true if version needs initialization
     */
    public boolean needsVersionInit(T entity) {
        if (entity == null || versionProperty == null) {
            return false;
        }
        Object currentVersion = versionProperty.getProperty().get(entity);
        return currentVersion == null;
    }

    /**
     * Get the next version value for an update.
     *
     * @param entity the entity
     * @return the next version value, or null if no version property
     */
    public Object getNextVersionValue(T entity) {
        if (entity == null || versionProperty == null) {
            return null;
        }
        Object currentVersion = versionProperty.getProperty().get(entity);
        if (currentVersion instanceof Number number) {
            return number.longValue() + 1;
        } else if (currentVersion instanceof Instant instant) {
            return Instant.now();
        }
        return currentVersion;
    }

}
