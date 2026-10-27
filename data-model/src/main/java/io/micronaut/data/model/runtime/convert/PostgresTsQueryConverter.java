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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchQueryParser;
import io.micronaut.data.model.search.SearchTerm;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

/**
 * Converts a search string in the portable grammar into PostgreSQL {@code tsquery} syntax for a
 * {@link MatchMode}, so the generated SQL can always use {@code to_tsquery(config, ?)}.
 *
 * <p>Lexemes are quoted, which makes any user input safe for {@code to_tsquery}; a quoted
 * multi-word lexeme becomes a phrase. Negated terms are joined with {@code & !}.</p>
 *
 * @since 5.4.0
 */
@Internal
public abstract class PostgresTsQueryConverter implements TextSearchQueryConverter {

    private static final String MATCH_NOTHING = "'mn_no_match_0'";

    /**
     * @param mode The match mode
     * @return The class name of the converter for the mode
     */
    public static String converterFor(MatchMode mode) {
        return switch (mode) {
            case ANY -> Any.class.getName();
            case ALL -> All.class.getName();
            case PHRASE -> Phrase.class.getName();
            case PREFIX -> Prefix.class.getName();
            case FUZZY -> FuzzyPositive.class.getName();
        };
    }

    /**
     * @param negated Whether the converter renders the negated terms
     * @return The class name of the converter rendering the terms of a fuzzy match as the
     * space-separated list {@code unnest(string_to_array(?, ' '))} splits
     */
    public static String fuzzyConverterFor(boolean negated) {
        return negated ? FuzzyNegated.class.getName() : FuzzyPositive.class.getName();
    }

    /**
     * Renders the terms of a fuzzy match as a space-separated list for {@code pg_trgm} word similarity;
     * phrases contribute their words and {@code OR} groups are flattened, as every term matches on its own.
     *
     * @param query The search string
     * @param negated Whether to render the negated terms instead of the positive ones
     * @return The terms, empty when there are none
     */
    public static String renderFuzzy(@Nullable String query, boolean negated) {
        StringJoiner terms = new StringJoiner(" ");
        for (SearchTerm term : SearchQueryParser.terms(query)) {
            if (term.negated() == negated) {
                for (String word : term.text().trim().split("\\s+")) {
                    if (!word.isEmpty()) {
                        terms.add(word);
                    }
                }
            }
        }
        return terms.toString();
    }

    /**
     * Renders a search string as a {@code tsquery} for the mode.
     *
     * @param query The search string
     * @param mode The match mode
     * @return The {@code tsquery} source text
     */
    public static String render(@Nullable String query, MatchMode mode) {
        if (mode == MatchMode.PHRASE) {
            String phrase = query == null ? "" : query.replace("\"", "").trim();
            return phrase.isEmpty() ? MATCH_NOTHING : quote(phrase);
        }
        List<List<SearchTerm>> groups = SearchQueryParser.parse(query);
        StringJoiner disjunction = new StringJoiner(" | ");
        for (List<SearchTerm> group : groups) {
            String rendered = renderGroup(group, mode);
            if (rendered != null) {
                disjunction.add(groups.size() > 1 ? "(" + rendered + ")" : rendered);
            }
        }
        return disjunction.length() == 0 ? MATCH_NOTHING : disjunction.toString();
    }

    @Nullable
    private static String renderGroup(List<SearchTerm> group, MatchMode mode) {
        StringJoiner positive = new StringJoiner(mode == MatchMode.ANY ? " | " : " & ");
        StringBuilder negative = new StringBuilder();
        for (SearchTerm term : group) {
            String lexeme = quote(term.text());
            if (mode == MatchMode.PREFIX && !term.phrase()) {
                lexeme += ":*";
            }
            if (term.negated()) {
                negative.append(" & !").append(lexeme);
            } else {
                positive.add(lexeme);
            }
        }
        if (positive.length() == 0) {
            return null;
        }
        String result = positive.toString();
        if (!negative.isEmpty()) {
            boolean wrap = mode == MatchMode.ANY && group.stream().filter(t -> !t.negated()).count() > 1;
            result = (wrap ? "(" + result + ")" : result) + negative;
        }
        return result;
    }

    private static String quote(String lexeme) {
        // inside a quoted lexeme a backslash escapes the next character, so it is doubled like the quote
        return "'" + lexeme.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
        return render(entityValue, mode());
    }

    @Override
    @Nullable
    public String convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        return persistedValue;
    }

    /**
     * @return The match mode this converter renders
     */
    protected abstract MatchMode mode();

    /**
     * {@link MatchMode#ANY} converter.
     */
    /**
     * The positive terms of a fuzzy match.
     */
    @Singleton
    public static final class FuzzyPositive extends PostgresTsQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.FUZZY;
        }

        @Override
        public String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
            return renderFuzzy(entityValue, false);
        }
    }

    /**
     * The negated terms of a fuzzy match.
     */
    @Singleton
    public static final class FuzzyNegated extends PostgresTsQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.FUZZY;
        }

        @Override
        public String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
            return renderFuzzy(entityValue, true);
        }
    }

    @Singleton
    public static final class Any extends PostgresTsQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ANY;
        }
    }

    /**
     * {@link MatchMode#ALL} converter.
     */
    @Singleton
    public static final class All extends PostgresTsQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ALL;
        }
    }

    /**
     * {@link MatchMode#PHRASE} converter.
     */
    @Singleton
    public static final class Phrase extends PostgresTsQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.PHRASE;
        }
    }

    /**
     * {@link MatchMode#PREFIX} converter.
     */
    @Singleton
    public static final class Prefix extends PostgresTsQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.PREFIX;
        }
    }
}
