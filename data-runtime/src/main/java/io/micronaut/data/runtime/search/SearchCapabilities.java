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
 * ordering. Each store registers one bean per datasource or client; a feature that is not reported
 * is rejected with an {@link io.micronaut.data.model.search.UnsupportedSearchFeatureException}.
 *
 * @author Graeme Rocher
 * @since 5.4.0
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
