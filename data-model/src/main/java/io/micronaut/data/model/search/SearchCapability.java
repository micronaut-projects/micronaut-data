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
package io.micronaut.data.model.search;

import io.micronaut.core.annotation.Experimental;

/**
 * A full-text search feature a backend may or may not support. Every backend supports
 * {@link MatchMode#ANY}, {@link MatchMode#ALL} and ordering by score; everything else is a capability.
 *
 * @since 5.4.0
 */
@Experimental
public enum SearchCapability {
    /**
     * {@link MatchMode#PHRASE}.
     */
    PHRASE,
    /**
     * {@link MatchMode#PREFIX}.
     */
    PREFIX,
    /**
     * {@link MatchMode#FUZZY}.
     */
    FUZZY,
    /**
     * Query-time boosting of individual fields.
     */
    BOOST,
    /**
     * Restricting a search to the named properties. MongoDB {@code $text} always searches every
     * indexed field, so a property keyword such as {@code findByTitleSearch} matches any search field there.
     */
    FIELD_SCOPE,
    /**
     * Highlighted fragments in the results.
     */
    HIGHLIGHT,
    /**
     * A per-row language override through {@code @Language}.
     */
    LANGUAGE_OVERRIDE,
    /**
     * Vector similarity search.
     */
    VECTOR,
    /**
     * Combined text and vector ranking.
     */
    HYBRID,
    /**
     * A minimum relevance score as a restriction, the {@code Score} parameter of a derived search method.
     */
    MIN_SCORE,
    /**
     * The maximum edit distance of a fuzzy match ({@code SearchQuery.fuzzy(maxEdits)}) is honoured.
     */
    FUZZINESS
}
