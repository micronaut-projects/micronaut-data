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
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.runtime.search.SearchCapabilities;
import io.micronaut.data.runtime.search.SearchIndexOperations;
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SearchIndexOperations} of the document search stores: the index of an entity holds its documents,
 * so {@link #drop} removes them too, and {@link #sync} refreshes the index.
 *
 * @since 5.4.0
 */
@Experimental
@Singleton
@Requires(beans = DocumentSearchRepositoryOperations.class)
public final class DocumentSearchIndexOperations implements SearchIndexOperations, SearchCapabilities {

    // Elasticsearch and OpenSearch serve every portable feature natively, vectors through dense_vector and knn_vector
    private static final Set<SearchCapability> CAPABILITIES = EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY,
        SearchCapability.FUZZINESS, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST, SearchCapability.HIGHLIGHT, SearchCapability.LANGUAGE_OVERRIDE,
        SearchCapability.MIN_SCORE, SearchCapability.VECTOR, SearchCapability.HYBRID);

    private final List<DocumentSearchRepositoryOperations> operations;
    private final RuntimeEntityRegistry runtimeEntityRegistry;

    DocumentSearchIndexOperations(List<DocumentSearchRepositoryOperations> operations, RuntimeEntityRegistry runtimeEntityRegistry) {
        this.operations = operations;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public Set<SearchCapability> getSearchCapabilities(Class<?> entityType) {
        return runtimeEntityRegistry.getEntity(entityType).getAnnotationMetadata().hasAnnotation(Searchable.class) ? CAPABILITIES : Set.of();
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
            if (!ops.getTransport().indexExists(index)) {
                ops.getTransport().createIndex(index, SearchIndexDefinitions.mappingFor(entity, ops.getTransport()));
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
