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
package io.micronaut.data.search.document.init;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.search.SearchIndexOperations;
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * {@link SearchIndexOperations} of the document search stores: the index of an entity holds its documents,
 * so {@link #drop} removes them too, and {@link #sync} refreshes the index.
 *
 * @since 5.3.0
 */
@Experimental
@Singleton
@Requires(beans = DocumentSearchRepositoryOperations.class)
public final class DocumentSearchIndexOperations implements SearchIndexOperations {

    private final List<DocumentSearchRepositoryOperations> operations;
    private final RuntimeEntityRegistry runtimeEntityRegistry;

    DocumentSearchIndexOperations(List<DocumentSearchRepositoryOperations> operations, RuntimeEntityRegistry runtimeEntityRegistry) {
        this.operations = operations;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public boolean exists(Class<?> entityType) {
        for (DocumentSearchRepositoryOperations ops : operations) {
            if (!ops.getTransport().indexExists(ops.getIndexName(entityType))) {
                return false;
            }
        }
        return !operations.isEmpty();
    }

    @Override
    public void create(Class<?> entityType) {
        RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(entityType);
        for (DocumentSearchRepositoryOperations ops : operations) {
            String index = ops.getIndexName(entityType);
            SearchTransport transport = ops.getTransport();
            if (!transport.indexExists(index)) {
                createIndex(transport, index, SearchIndexDefinitions.mappingFor(entity, transport));
            }
        }
    }

    /**
     * Creates an index that did not exist, leaving alone one that a concurrent create, or the startup index
     * creator, made in the meantime: the store then rejects the create as an index that exists.
     */
    private static void createIndex(SearchTransport transport, String index, String mapping) {
        try {
            transport.createIndex(index, mapping);
        } catch (DataAccessException e) {
            if (!transport.indexExists(index)) {
                throw e;
            }
        }
    }

    @Override
    public void drop(Class<?> entityType) {
        for (DocumentSearchRepositoryOperations ops : operations) {
            String index = ops.getIndexName(entityType);
            if (ops.getTransport().indexExists(index)) {
                ops.getTransport().deleteIndex(index);
            }
        }
    }

    @Override
    public void sync(Class<?> entityType) {
        for (DocumentSearchRepositoryOperations ops : operations) {
            ops.getTransport().refresh(ops.getIndexName(entityType));
        }
    }
}
