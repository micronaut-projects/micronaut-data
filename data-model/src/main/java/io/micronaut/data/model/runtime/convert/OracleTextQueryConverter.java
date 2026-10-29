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

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Converts a search string in the portable grammar into Oracle Text {@code CONTAINS} query syntax for a
 * {@link MatchMode}.
 *
 * <p>Terms are escaped in braces so reserved words and operators in user input are matched literally.
 * A single-field search binds the {@link Part#FULL full} query, with negated terms applied through the
 * {@code ~} operator. A search over several fields binds the {@link Part#POSITIVE positive} terms per
 * field and the {@link Part#NEGATED negated} terms once more, so a negated term excludes a row whichever
 * field it appears in.</p>
 *
 * @since 5.4.0
 */
@Internal
public abstract class OracleTextQueryConverter implements TextSearchQueryConverter {

    /**
     * A {@code CONTAINS} query that matches nothing, bound for an empty search string: a term and its
     * negation, false for every row whatever it contains.
     */
    public static final String MATCH_NOTHING = "{mn_no_match_0} ~ {mn_no_match_0}";

    /**
     * The shortest term {@code SearchPrefix} expands with {@code %}; a shorter one is matched exactly, as a
     * one-letter prefix expands to more terms than Oracle Text's wildcard limit allows (DRG-51030).
     */
    public static final int MIN_PREFIX_LENGTH = 2;

    private static final String FUZZY_SUFFIX = ",60,100)";

    /**
     * Which part of the search string a converter renders.
     */
    public enum Part {
        /**
         * Positive and negated terms in one query.
         */
        FULL,
        /**
         * Positive terms only.
         */
        POSITIVE,
        /**
         * Negated terms only, as an any-term query.
         */
        NEGATED
    }

    /**
     * @param mode The match mode
     * @param part The part of the query to render
     * @return The class name of the converter
     */
    public static String converterFor(MatchMode mode, Part part) {
        if (part == Part.NEGATED) {
            return Negated.class.getName();
        }
        boolean positive = part == Part.POSITIVE;
        return switch (mode) {
            case ANY -> positive ? AnyPositive.class.getName() : Any.class.getName();
            case ALL -> positive ? AllPositive.class.getName() : All.class.getName();
            case PHRASE -> positive ? PhrasePositive.class.getName() : Phrase.class.getName();
            case PREFIX -> positive ? PrefixPositive.class.getName() : Prefix.class.getName();
            case FUZZY -> positive ? FuzzyPositive.class.getName() : Fuzzy.class.getName();
        };
    }

    /**
     * Renders a search string as an Oracle Text query.
     *
     * @param query The search string
     * @param mode The match mode
     * @param part The part to render
     * @return The {@code CONTAINS} query text
     */
    public static String render(@Nullable String query, MatchMode mode, Part part) {
        if (query == null) {
            return MATCH_NOTHING;
        }
        if (part == Part.NEGATED) {
            return renderNegated(query);
        }
        if (mode == MatchMode.PHRASE) {
            String phrase = escape(query.replace("\"", ""));
            return phrase == null ? MATCH_NOTHING : phrase;
        }
        List<String> rendered = new ArrayList<>();
        for (List<SearchTerm> group : SearchQueryParser.parse(query)) {
            String groupQuery = renderGroup(group, mode, part == Part.FULL);
            if (groupQuery != null) {
                rendered.add(groupQuery);
            }
        }
        return joinGroups(rendered);
    }

    /** The negated terms of a query as one disjunction, for the exclusion of a multi-field search. */
    private static String renderNegated(String query) {
        StringJoiner negated = new StringJoiner(" | ");
        for (SearchTerm term : SearchQueryParser.terms(query)) {
            String escaped = escape(term.text());
            if (term.negated() && escaped != null) {
                negated.add(escaped);
            }
        }
        return negated.length() == 0 ? MATCH_NOTHING : negated.toString();
    }

    private static String joinGroups(List<String> rendered) {
        if (rendered.isEmpty()) {
            return MATCH_NOTHING;
        }
        if (rendered.size() == 1) {
            return rendered.get(0);
        }
        StringJoiner joiner = new StringJoiner(" | ");
        rendered.forEach(r -> joiner.add("(" + r + ")"));
        return joiner.toString();
    }

    @Nullable
    private static String renderGroup(List<SearchTerm> group, MatchMode mode, boolean applyNegation) {
        StringJoiner positive = new StringJoiner(mode == MatchMode.ALL || mode == MatchMode.PREFIX ? " & " : " | ");
        List<String> negated = new ArrayList<>();
        for (SearchTerm term : group) {
            String rendered = renderTerm(term, mode);
            if (rendered == null) {
                continue;
            }
            if (term.negated()) {
                negated.add(rendered);
            } else {
                positive.add(rendered);
            }
        }
        if (positive.length() == 0) {
            return null;
        }
        String result = positive.toString();
        if (applyNegation) {
            for (String term : negated) {
                result = "(" + result + ") ~ " + term;
            }
        }
        return result;
    }

    @Nullable
    private static String renderTerm(SearchTerm term, MatchMode mode) {
        if (term.phrase() || term.negated() || mode == MatchMode.ANY || mode == MatchMode.ALL) {
            return escape(term.text());
        }
        // an underscore is Oracle Text's single-character wildcard, so it goes with the other symbols
        String word = term.text().replaceAll("[^\\p{L}\\p{N}]", "");
        if (word.isEmpty()) {
            return null;
        }
        // a reserved word such as near or about is escaped in braces inside fuzzy(); braces do not combine with %
        if (mode == MatchMode.PREFIX) {
            return word.length() < MIN_PREFIX_LENGTH ? "{" + word + "}" : word + "%";
        }
        return "fuzzy({" + word + "}" + FUZZY_SUFFIX;
    }

    /** A term in braces, matched literally; {@code null} when nothing but braces or spaces remains. */
    @Nullable
    private static String escape(String text) {
        String literal = text.replace("}", "").replace("{", "").trim();
        return literal.isEmpty() ? null : "{" + literal + "}";
    }

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
        return render(entityValue, mode(), part());
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
     * @return The part this converter renders
     */
    protected Part part() {
        return Part.FULL;
    }

    /**
     * Any-term converter.
     */
    @Singleton
    public static class Any extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ANY;
        }
    }

    /**
     * All-terms converter.
     */
    @Singleton
    public static class All extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ALL;
        }
    }

    /**
     * Phrase converter.
     */
    @Singleton
    public static class Phrase extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.PHRASE;
        }
    }

    /**
     * Prefix converter.
     */
    @Singleton
    public static class Prefix extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.PREFIX;
        }
    }

    /**
     * Fuzzy converter.
     */
    @Singleton
    public static class Fuzzy extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.FUZZY;
        }
    }

    /**
     * Any-term converter, positive terms only.
     */
    @Singleton
    public static final class AnyPositive extends Any {
        @Override
        protected Part part() {
            return Part.POSITIVE;
        }
    }

    /**
     * All-terms converter, positive terms only.
     */
    @Singleton
    public static final class AllPositive extends All {
        @Override
        protected Part part() {
            return Part.POSITIVE;
        }
    }

    /**
     * Phrase converter, positive terms only.
     */
    @Singleton
    public static final class PhrasePositive extends Phrase {
        @Override
        protected Part part() {
            return Part.POSITIVE;
        }
    }

    /**
     * Prefix converter, positive terms only.
     */
    @Singleton
    public static final class PrefixPositive extends Prefix {
        @Override
        protected Part part() {
            return Part.POSITIVE;
        }
    }

    /**
     * Fuzzy converter, positive terms only.
     */
    @Singleton
    public static final class FuzzyPositive extends Fuzzy {
        @Override
        protected Part part() {
            return Part.POSITIVE;
        }
    }

    /**
     * Negated terms converter.
     */
    @Singleton
    public static final class Negated extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ANY;
        }

        @Override
        protected Part part() {
            return Part.NEGATED;
        }
    }
}
