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
     * Prepare multiple entities for batch insert.
     *
     * @param entities the entities
     */
    public void prepareForInsert(Iterable<T> entities) {
        if (entities == null) {
            return;
        }
        for (T entity : entities) {
            repositoryWriter.prepareForInsert(entity);
        }
    }

    /**
     * Prepare multiple entities for batch update.
     *
     * @param entities the entities
     */
    public void prepareForUpdate(Iterable<T> entities) {
        if (entities == null) {
            return;
        }
        for (T entity : entities) {
            repositoryWriter.prepareForUpdate(entity);
        }
    }
}
