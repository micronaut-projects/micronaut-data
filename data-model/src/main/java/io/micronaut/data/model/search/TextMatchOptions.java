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
package io.micronaut.data.model.search;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The options of a full-text match beyond its {@link MatchMode}: query-time boosts of the searched
 * properties, the maximum edit distance of a {@link MatchMode#FUZZY} match and the language the query is
 * analysed with. A store that cannot
 * honour an option throws {@link UnsupportedSearchFeatureException} with the matching
 * {@link SearchCapability}.
 *
 * <p>Options are created with {@link #builder()}, or from a {@link SearchQuery} with {@link #of(SearchQuery)},
 * so options added later do not change how existing code creates them.</p>
 *
 * <p>A boost is keyed by the property path of a matched field, such as {@code title} or {@code address.city},
 * and must name one of the fields of the match it is passed to; the criteria builder rejects any other key
 * with an {@link IllegalArgumentException}. An edit distance only applies to a {@link MatchMode#FUZZY} match:
 * the criteria builder rejects one with another mode. To match the terms without edits, use
 * {@link MatchMode#ANY}.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public final class TextMatchOptions {

    /**
     * No boosts, the default fuzziness and the entity language.
     */
    public static final TextMatchOptions NONE = new TextMatchOptions(Map.of(), 0, null);

    private final Map<String, Double> boosts;
    private final int fuzziness;
    private final @Nullable String language;

    private TextMatchOptions(Map<String, Double> boosts, int fuzziness, @Nullable String language) {
        this.boosts = Collections.unmodifiableMap(new LinkedHashMap<>(boosts));
        this.fuzziness = fuzziness;
        this.language = language;
    }

    /**
     * @return A builder of options
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The boosts, the edit distance and the language of a {@link SearchQuery}.
     *
     * @param searchQuery The query
     * @return The options
     */
    public static TextMatchOptions of(SearchQuery searchQuery) {
        Builder builder = builder().fuzziness(searchQuery.getFuzziness()).language(searchQuery.getLanguage());
        searchQuery.getBoosts().forEach(builder::boost);
        return builder.build();
    }

    /**
     * @return The boost factor by property path; a boosted property replaces its index-time weight
     */
    public Map<String, Double> boosts() {
        return boosts;
    }

    /**
     * @return The maximum edit distance of a fuzzy match, {@code 0} for the store default
     */
    public int fuzziness() {
        return fuzziness;
    }

    /**
     * @return The language to analyse the query with, or {@code null} for the entity language
     */
    public @Nullable String language() {
        return language;
    }

    /**
     * @return Whether the options are the defaults
     */
    public boolean isDefault() {
        return boosts.isEmpty() && fuzziness == 0 && language == null;
    }

    /**
     * @param propertyPath The property path of a matched field, such as {@code title} or {@code address.city}
     * @return The boost of the property, or {@code null} when it is not boosted
     */
    @Nullable
    public Double boostFor(String propertyPath) {
        return boosts.get(propertyPath);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextMatchOptions that && fuzziness == that.fuzziness && boosts.equals(that.boosts)
            && Objects.equals(language, that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boosts, fuzziness, language);
    }

    @Override
    public String toString() {
        return "TextMatchOptions{boosts=" + boosts + ", fuzziness=" + fuzziness + ", language=" + language + '}';
    }

    /**
     * Builder of {@link TextMatchOptions}.
     */
    public static final class Builder {
        private final Map<String, Double> boosts = new LinkedHashMap<>();
        private int fuzziness;
        private @Nullable String language;

        private Builder() {
        }

        /**
         * Boosts the matches of a field.
         *
         * @param propertyPath The property path of one of the matched fields, such as {@code title} or {@code address.city}
         * @param boost The boost factor, a finite number above 0
         * @return This builder
         * @throws IllegalArgumentException When the boost is not a finite number above 0
         */
        public Builder boost(String propertyPath, double boost) {
            Objects.requireNonNull(propertyPath, "propertyPath must not be null");
            if (!Double.isFinite(boost) || boost <= 0) {
                throw new IllegalArgumentException("The boost of " + propertyPath + " must be a finite number above 0, got " + boost);
            }
            boosts.put(propertyPath, boost);
            return this;
        }

        /**
         * Sets the maximum edit distance of a {@link MatchMode#FUZZY} match.
         *
         * @param maxEdits The edit distance, {@code 0} for the store default
         * @return This builder
         * @throws IllegalArgumentException When the edit distance is negative
         */
        public Builder fuzziness(int maxEdits) {
            if (maxEdits < 0) {
                throw new IllegalArgumentException("The fuzziness cannot be negative: " + maxEdits);
            }
            this.fuzziness = maxEdits;
            return this;
        }

        /**
         * Analyses the query with another language than the entity's {@code @Searchable(language)}.
         *
         * @param language The language, or {@code null} or blank for the entity language
         * @return This builder
         */
        public Builder language(@Nullable String language) {
            this.language = language == null || language.isBlank() ? null : language;
            return this;
        }

        /**
         * @return The options
         */
        public TextMatchOptions build() {
            return boosts.isEmpty() && fuzziness == 0 && language == null ? NONE : new TextMatchOptions(boosts, fuzziness, language);
        }
    }
}
