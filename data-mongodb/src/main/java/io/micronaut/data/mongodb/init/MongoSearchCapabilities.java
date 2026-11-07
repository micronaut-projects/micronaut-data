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
package io.micronaut.data.mongodb.init;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.document.mongo.MongoAnnotations;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.runtime.search.SearchCapabilities;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.Set;

/**
 * The search features MongoDB honours: MongoDB Search ({@code $search}) for an entity with a
 * {@code @MongoSearchIndex}, else the {@code $text} operator over the collection's text index.
 *
 * @author Graeme Rocher
 * @since 5.4.0
 */
@Experimental
@Singleton
public final class MongoSearchCapabilities implements SearchCapabilities {

    private static final Set<SearchCapability> SEARCH = EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY,
        SearchCapability.FUZZINESS, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST, SearchCapability.HIGHLIGHT, SearchCapability.MIN_SCORE,
        SearchCapability.VECTOR, SearchCapability.HYBRID);
    // $text searches the whole text index: no prefix, fuzzy, field scope, highlight or score threshold
    private static final Set<SearchCapability> TEXT = EnumSet.of(SearchCapability.PHRASE, SearchCapability.LANGUAGE_OVERRIDE);

    private final RuntimeEntityRegistry runtimeEntityRegistry;

    /**
     * @param runtimeEntityRegistry The entity registry
     */
    MongoSearchCapabilities(RuntimeEntityRegistry runtimeEntityRegistry) {
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public Set<SearchCapability> getSearchCapabilities(Class<?> entityType) {
        AnnotationMetadata annotationMetadata = runtimeEntityRegistry.getEntity(entityType).getAnnotationMetadata();
        if (annotationMetadata.hasAnnotation(MongoAnnotations.SEARCH_INDEX)) {
            return SEARCH;
        }
        return annotationMetadata.hasAnnotation(Searchable.class) ? TEXT : Set.of();
    }
}
