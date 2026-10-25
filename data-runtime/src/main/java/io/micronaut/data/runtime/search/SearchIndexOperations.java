/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.search;

import io.micronaut.core.annotation.Experimental;

/**
 * Manages the full-text search index of a {@link io.micronaut.data.annotation.search.Searchable} entity at
 * runtime, beyond the create-if-missing of schema generation: one bean per store (a JDBC datasource, the
 * MongoDB client, the document search transports). An R2DBC datasource has none, as the operations block.
 *
 * @since 5.4.0
 */
@Experimental
public interface SearchIndexOperations {

    /**
     * @param entityType The entity
     * @return Whether every index backing the entity's search exists
     */
    boolean exists(Class<?> entityType);

    /**
     * Creates the indexes backing the entity's search; an index that exists is left alone.
     *
     * @param entityType The entity
     */
    void create(Class<?> entityType);

    /**
     * Drops the indexes backing the entity's search. On a document store this drops the index holding
     * the documents themselves, so the data is gone: do not expose it to untrusted callers.
     *
     * @param entityType The entity
     */
    void drop(Class<?> entityType);

    /**
     * Makes the pending writes searchable: an Oracle Text {@code SYNC_INDEX}, a document store refresh,
     * or waiting for a MongoDB Search index to be queryable. Stores whose indexes are always current
     * return at once.
     *
     * @param entityType The entity
     */
    void sync(Class<?> entityType);

    /**
     * Drops and creates the indexes. On a document store this deletes the stored documents.
     *
     * @param entityType The entity
     */
    default void recreate(Class<?> entityType) {
        drop(entityType);
        create(entityType);
    }
}
