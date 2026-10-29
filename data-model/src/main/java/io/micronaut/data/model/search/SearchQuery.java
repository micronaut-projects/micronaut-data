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
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A programmatic full-text search query. The query carries no backend types; each backend compiles
 * it to its native form and reports the options it cannot honour through
 * {@link UnsupportedSearchFeatureException}.
 *
 * @since 5.4.0
 */
@Experimental
public final class SearchQuery {

    private final String query;
    private final Set<String> fields;
    private final MatchMode mode;
    private final int fuzziness;
    private final Map<String, Double> boosts;
    private final Set<String> highlightFields;
    @Nullable
    private final Double minScore;
    @Nullable
    private final String language;

    private SearchQuery(Builder builder) {
        this.query = builder.query;
        this.fields = Collections.unmodifiableSet(new LinkedHashSet<>(builder.fields));
        this.mode = builder.mode;
        this.fuzziness = builder.mode == MatchMode.FUZZY ? builder.fuzziness : 0;
        this.boosts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.boosts));
        this.highlightFields = Collections.unmodifiableSet(new LinkedHashSet<>(builder.highlightFields));
        this.minScore = builder.minScore;
        this.language = builder.language;
    }

    /**
     * Starts a query for the given search string.
     *
     * @param query The search string, in the grammar of {@link SearchQueryParser}
     * @return The builder
     */
    public static Builder builder(String query) {
        return new Builder(query);
    }

    /**
     * Creates a query that matches any term in every search field.
     *
     * @param query The search string
     * @return The query
     */
    public static SearchQuery of(String query) {
        return new Builder(query).build();
    }

    /**
     * @return The search string
     */
    public String getQuery() {
        return query;
    }

    /**
     * @return The entity properties to search, empty for every {@code @SearchField}
     */
    public Set<String> getFields() {
        return fields;
    }

    /**
     * @return The match mode
     */
    public MatchMode getMode() {
        return mode;
    }

    /**
     * @return The maximum edit distance for {@link MatchMode#FUZZY}, {@code 0} for the backend default
     */
    public int getFuzziness() {
        return fuzziness;
    }

    /**
     * @return Query-time boosts by property name
     */
    public Map<String, Double> getBoosts() {
        return boosts;
    }

    /**
     * @return The properties to return highlighted fragments for
     */
    public Set<String> getHighlightFields() {
        return highlightFields;
    }

    /**
     * @return The minimum relevance score a result must have, or {@code null}
     */
    @Nullable
    public Double getMinScore() {
        return minScore;
    }

    /**
     * @return The language to analyse the query with, or {@code null} for the entity default
     */
    @Nullable
    public String getLanguage() {
        return language;
    }

    /**
     * @return The parsed terms of {@link #getQuery()}
     */
    public List<SearchTerm> getTerms() {
        return SearchQueryParser.terms(query);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SearchQuery that)) {
            return false;
        }
        return fuzziness == that.fuzziness
            && query.equals(that.query)
            && fields.equals(that.fields)
            && mode == that.mode
            && boosts.equals(that.boosts)
            && highlightFields.equals(that.highlightFields)
            && Objects.equals(minScore, that.minScore)
            && Objects.equals(language, that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, fields, mode, fuzziness, boosts, highlightFields, minScore, language);
    }

    @Override
    public String toString() {
        // the search string is user input, so only its length is shown
        return "SearchQuery{queryLength=" + (query == null ? 0 : query.length()) + ", fields=" + fields + ", mode=" + mode + '}';
    }

    /**
     * Builder for {@link SearchQuery}.
     */
    public static final class Builder {
        private final String query;
        private final Set<String> fields = new LinkedHashSet<>();
        private MatchMode mode = MatchMode.ANY;
        private int fuzziness;
        private final Map<String, Double> boosts = new LinkedHashMap<>();
        private final Set<String> highlightFields = new LinkedHashSet<>();
        @Nullable
        private Double minScore;
        @Nullable
        private String language;

        private Builder(String query) {
            this.query = Objects.requireNonNull(query, "query must not be null");
        }

        /**
         * Restricts the search to the given properties.
         *
         * @param fields The property names
         * @return This builder
         */
        public Builder fields(String... fields) {
            Collections.addAll(this.fields, fields);
            return this;
        }

        /**
         * @param mode The match mode
         * @return This builder
         */
        public Builder mode(MatchMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode must not be null");
            return this;
        }

        /**
         * Requests fuzzy matching with the given maximum edit distance.
         *
         * @param maxEdits The maximum edit distance from {@code 0} to {@code 2}, {@code 0} for the backend default
         * @return This builder
         * @throws IllegalArgumentException When the edit distance is outside that range
         */
        public Builder fuzzy(int maxEdits) {
            if (maxEdits < 0 || maxEdits > 2) {
                throw new IllegalArgumentException("The fuzzy edit distance must be between 0 and 2: " + maxEdits);
            }
            this.mode = MatchMode.FUZZY;
            this.fuzziness = maxEdits;
            return this;
        }

        /**
         * Boosts matches in the given property.
         *
         * @param field The property name
         * @param boost The boost factor
         * @return This builder
         */
        public Builder boost(String field, double boost) {
            boosts.put(field, boost);
            return this;
        }

        /**
         * Requests highlighted fragments for the given properties.
         *
         * @param fields The property names
         * @return This builder
         */
        public Builder highlight(String... fields) {
            Collections.addAll(this.highlightFields, fields);
            return this;
        }

        /**
         * @param minScore The minimum relevance score
         * @return This builder
         */
        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * @param language The language to analyse the query with
         * @return This builder
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * @return The query
         */
        public SearchQuery build() {
            return new SearchQuery(this);
        }
    }
}
