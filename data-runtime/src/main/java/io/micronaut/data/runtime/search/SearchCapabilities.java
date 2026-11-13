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
package io.micronaut.data.runtime.search;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.search.SearchCapability;

import java.util.Set;

/**
 * Reports which search features a store honours for an entity, beyond term matching and score
 * ordering; a feature that is not reported is rejected with an
 * {@link io.micronaut.data.model.search.UnsupportedSearchFeatureException}.
 *
 * <p>There is no aggregate bean: each store registers its own, one per JDBC or R2DBC datasource, named
 * after the datasource, one for MongoDB ({@code MongoSearchCapabilities}) and one for Elasticsearch and
 * OpenSearch ({@code DocumentSearchIndexOperations}). An unqualified injection resolves to the only bean,
 * or else to the bean of the datasource named {@code default}, which is primary, even when the entity is
 * held by another store; without a single primary bean, as with MongoDB and Elasticsearch, it fails with a
 * {@link io.micronaut.context.exceptions.NonUniqueBeanException}. With several stores, inject the bean of
 * the store that holds the entity: by datasource name ({@code @Named("other") SearchCapabilities}) or by
 * the implementation type, or all of them as a {@code List<SearchCapabilities>}.</p>
 *
 * <p>A bean answers for any {@code @Searchable} entity from the store's dialect or mapping, not from
 * where the entity is stored, and it does not probe the server version: the JDBC bean of a PostgreSQL
 * datasource reports {@link io.micronaut.data.model.search.SearchCapability#FUZZY} only when the
 * {@code pg_trgm} extension is installed, while the R2DBC bean reports it from the dialect, so a
 * fuzzy search on a database without the extension fails there.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public interface SearchCapabilities {

    /**
     * @param entityType The entity type
     * @return The search features the store honours for the entity, empty when the entity is not searchable
     */
    Set<SearchCapability> getSearchCapabilities(Class<?> entityType);

    /**
     * @param entityType The entity type
     * @param capability The search feature
     * @return Whether the store honours the feature for the entity
     */
    default boolean supports(Class<?> entityType, SearchCapability capability) {
        return getSearchCapabilities(entityType).contains(capability);
    }
}
