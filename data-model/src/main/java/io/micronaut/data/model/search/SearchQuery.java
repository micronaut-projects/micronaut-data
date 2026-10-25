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
import io.micronaut.data.model.vector.Vector;
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

    /**
     * The default number of hits each ranking of a hybrid search contributes.
     */
    public static final int DEFAULT_CANDIDATES = 100;

    /**
     * The default constant {@code k} of the reciprocal rank fusion, as in the original paper.
     */
    public static final int DEFAULT_RANK_CONSTANT = 60;

    private final String query;
    private final Set<String> fields;
    private final MatchMode mode;
    private final int fuzziness;
    private final Map<String, Double> boosts;
    private final Set<String> highlightFields;
    private final boolean highlightDeclaredFields;
    @Nullable
    private final Double minScore;
    @Nullable
    private final String language;
    @Nullable
    private final String vectorProperty;
    @Nullable
    private final Vector vector;
    private final double textWeight;
    private final double vectorWeight;
    private final int candidates;
    private final int rankConstant;

    private SearchQuery(Builder builder) {
        this.query = builder.query;
        this.vectorProperty = builder.vectorProperty;
        this.vector = builder.vector;
        this.textWeight = builder.textWeight;
        this.vectorWeight = builder.vectorWeight;
        this.candidates = builder.candidates;
        this.rankConstant = builder.rankConstant;
        this.fields = Collections.unmodifiableSet(new LinkedHashSet<>(builder.fields));
        this.mode = builder.mode;
        this.fuzziness = builder.fuzziness;
        this.boosts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.boosts));
        this.highlightFields = Collections.unmodifiableSet(new LinkedHashSet<>(builder.highlightFields));
        this.highlightDeclaredFields = builder.highlightDeclaredFields;
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
     * @return Whether the {@code @SearchField(highlightable = true)} properties are highlighted, in
     * addition to {@link #getHighlightFields()}
     */
    public boolean isHighlightingDeclaredFields() {
        return highlightDeclaredFields;
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

    /**
     * @return The vector property a hybrid search ranks by, or {@code null} for a text-only search
     */
    @Nullable
    public String getVectorProperty() {
        return vectorProperty;
    }

    /**
     * @return The query vector of a hybrid search, or {@code null} for a text-only search
     */
    @Nullable
    public Vector getVector() {
        return vector;
    }

    /**
     * @return Whether the query combines a text match with a vector similarity
     */
    public boolean isHybrid() {
        return vector != null;
    }

    /**
     * @return The weight of the text rank in a hybrid score
     */
    public double getTextWeight() {
        return textWeight;
    }

    /**
     * @return The weight of the vector rank in a hybrid score
     */
    public double getVectorWeight() {
        return vectorWeight;
    }

    /**
     * @return How many hits each ranking of a hybrid search contributes
     */
    public int getCandidates() {
        return candidates;
    }

    /**
     * @return The constant {@code k} of the reciprocal rank fusion {@code 1 / (k + rank)}
     */
    public int getRankConstant() {
        return rankConstant;
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
            && highlightDeclaredFields == that.highlightDeclaredFields
            && Objects.equals(minScore, that.minScore)
            && Objects.equals(language, that.language)
            && Objects.equals(vectorProperty, that.vectorProperty)
            && Objects.equals(vector, that.vector)
            && textWeight == that.textWeight
            && vectorWeight == that.vectorWeight
            && candidates == that.candidates
            && rankConstant == that.rankConstant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, fields, mode, fuzziness, boosts, highlightFields, highlightDeclaredFields, minScore, language,
            vectorProperty, vector, textWeight, vectorWeight, candidates, rankConstant);
    }

    @Override
    public String toString() {
        return "SearchQuery{query='" + query + "', fields=" + fields + ", mode=" + mode + '}';
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
        private boolean highlightDeclaredFields;
        @Nullable
        private Double minScore;
        @Nullable
        private String language;
        @Nullable
        private String vectorProperty;
        @Nullable
        private Vector vector;
        private double textWeight = 1d;
        private double vectorWeight = 1d;
        private int candidates = DEFAULT_CANDIDATES;
        private int rankConstant = DEFAULT_RANK_CONSTANT;

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
         * @param maxEdits The maximum edit distance, {@code 0} for the backend default
         * @return This builder
         */
        public Builder fuzzy(int maxEdits) {
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
         * Requests highlighted fragments for the given properties, or, without arguments, for every
         * {@code @SearchField(highlightable = true)} property of the entity.
         *
         * @param fields The property names
         * @return This builder
         */
        public Builder highlight(String... fields) {
            if (fields.length == 0) {
                highlightDeclaredFields = true;
            }
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
         * Analyses the query with another language than the entity's. The SQL stores render the
         * language inside the statement, so only a configuration name of letters, digits and
         * underscores is accepted; pass an application constant, not user input.
         *
         * @param language The language to analyse the query with
         * @return This builder
         * @throws IllegalArgumentException When the language holds other characters
         */
        public Builder language(String language) {
            TextSearchSupport.requireLanguageName(language);
            this.language = language;
            return this;
        }

        /**
         * Makes the search hybrid: the hits of the text match and the nearest neighbours of the given
         * vector are merged with reciprocal rank fusion, so a row matching both ranks first.
         *
         * @param property The vector property of the entity
         * @param vector The query vector
         * @return This builder
         */
        public Builder vector(String property, Vector vector) {
            this.vectorProperty = Objects.requireNonNull(property, "property must not be null");
            this.vector = Objects.requireNonNull(vector, "vector must not be null");
            return this;
        }

        /**
         * @param textWeight The weight of the text rank in the hybrid score, {@code 1} by default
         * @return This builder
         */
        public Builder textWeight(double textWeight) {
            this.textWeight = textWeight;
            return this;
        }

        /**
         * @param vectorWeight The weight of the vector rank in the hybrid score, {@code 1} by default
         * @return This builder
         */
        public Builder vectorWeight(double vectorWeight) {
            this.vectorWeight = vectorWeight;
            return this;
        }

        /**
         * @param candidates How many hits each ranking of a hybrid search contributes before fusion,
         * {@value #DEFAULT_CANDIDATES} by default
         * @return This builder
         */
        public Builder candidates(int candidates) {
            if (candidates < 1) {
                throw new IllegalArgumentException("candidates must be positive");
            }
            this.candidates = candidates;
            return this;
        }

        /**
         * @param rankConstant The constant {@code k} of the reciprocal rank fusion, {@value #DEFAULT_RANK_CONSTANT} by default
         * @return This builder
         */
        public Builder rankConstant(int rankConstant) {
            if (rankConstant < 0) {
                throw new IllegalArgumentException("rankConstant must not be negative");
            }
            this.rankConstant = rankConstant;
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
