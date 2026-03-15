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
import org.dizitart.no2.collection.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Writer for batch entity to Document conversion.
 * Used for bulk write operations (batch insert, batch update).
 *
 * @param <T> The entity type
 * @since 5.0.0
 */
@Internal
public final class CollectionWriter<T> {

    private final ObjectRepositoryWriter<T> repositoryWriter;

    public CollectionWriter(ObjectRepositoryWriter<T> repositoryWriter) {
        this.repositoryWriter = repositoryWriter;
    }

    /**
     * Convert multiple entities to Documents for batch writing.
     *
     * @param entities the entities
     * @return list of Documents
     */
    public List<Document> toDocuments(Iterable<T> entities) {
        if (entities == null) {
            return null;
        }
        List<Document> documents = new ArrayList<>();
        for (T entity : entities) {
            Document doc = repositoryWriter.toDocument(entity);
            if (doc != null) {
                documents.add(doc);
            }
        }
        return documents;
    }

    /**
     * Check if any entity needs version initialization for batch insert.
     *
     * @param entities the entities
     * @return true if any entity needs version initialization
     */
    public boolean needsVersionInit(Iterable<T> entities) {
        if (entities == null) {
            return false;
        }
        for (T entity : entities) {
            if (repositoryWriter.needsVersionInit(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the next version values for batch update.
     *
     * @param entities the entities
     * @return list of next version values (null entries for entities without version)
     */
    public List<Object> getNextVersionValues(Iterable<T> entities) {
        if (entities == null) {
            return null;
        }
        List<Object> versions = new ArrayList<>();
        for (T entity : entities) {
            versions.add(repositoryWriter.getNextVersionValue(entity));
        }
        return versions;
    }
}
