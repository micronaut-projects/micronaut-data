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
 * field it appears in. When the search string has several {@code OR} alternatives, the negated terms only
 * narrow their own alternative, so each field is matched with the full query instead and a negated term
 * excludes a row only when it appears in the field that matched.</p>
 *
 * @since 5.3.0
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
            case NATIVE -> Native.class.getName();
        };
    }

    /**
     * Checks that a native {@code CONTAINS} query closes every parenthesis and escape it opens. The query of a
     * JSON entity is bound into {@code '(' || ? || ') INPATH (/path)'}, so an unbalanced query such as
     * {@code x) INPATH (/other) OR (y} would move the search to another path; parentheses inside a
     * {@code {...}} escape or after a {@code \} are literal and not counted.
     *
     * @param query The native query
     * @return The query
     * @throws IllegalArgumentException When a parenthesis, a brace escape or a backslash escape is left open
     */
    static String checkNativeStructure(String query) {
        int depth = 0;
        boolean braced = false;
        int i = 0;
        while (i < query.length()) {
            char c = query.charAt(i);
            if (c == '\\') {
                // the next character is literal
                i++;
                if (i == query.length()) {
                    throw unbalanced(query);
                }
            } else if (braced) {
                braced = c != '}';
            } else if (c == '{') {
                braced = true;
            } else if (c == '(' || c == ')') {
                depth += c == '(' ? 1 : -1;
                if (depth < 0) {
                    throw unbalanced(query);
                }
            }
            i++;
        }
        if (depth != 0 || braced) {
            throw unbalanced(query);
        }
        return query;
    }

    private static IllegalArgumentException unbalanced(String query) {
        return new IllegalArgumentException("A native Oracle Text query must close every parenthesis, brace escape and backslash escape it opens: " + query);
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
        if (mode == MatchMode.NATIVE) {
            // the caller wrote CONTAINS query syntax
            return query.isBlank() ? MATCH_NOTHING : checkNativeStructure(query);
        }
        if (mode == MatchMode.PHRASE) {
            if (part == Part.NEGATED) {
                // a phrase has no negated terms: a - inside it is part of the phrase
                return MATCH_NOTHING;
            }
            String phrase = escape(query.replace("\"", ""));
            return phrase == null ? MATCH_NOTHING : phrase;
        }
        List<List<SearchTerm>> groups = alternatives(query);
        if (part == Part.NEGATED) {
            return renderNegated(groups);
        }
        // with several alternatives the negated terms go into each field's query, beside the terms they narrow
        boolean applyNegation = part == Part.FULL || groups.size() > 1;
        List<String> rendered = new ArrayList<>();
        for (List<SearchTerm> group : groups) {
            String groupQuery = renderGroup(group, mode, applyNegation);
            if (groupQuery != null) {
                rendered.add(groupQuery);
            }
        }
        return joinGroups(rendered);
    }

    /**
     * The {@code OR} alternatives of a query that can match a row: those with a positive term. The test does
     * not depend on the mode, so the positive and the negated converters of one search agree on the count.
     */
    private static List<List<SearchTerm>> alternatives(String query) {
        List<List<SearchTerm>> groups = new ArrayList<>();
        for (List<SearchTerm> group : SearchQueryParser.parse(query)) {
            if (group.stream().anyMatch(term -> !term.negated() && escape(term.text()) != null)) {
                groups.add(group);
            }
        }
        return groups;
    }

    /**
     * The negated terms of a single-alternative query as one disjunction, for the exclusion of a multi-field
     * search; a query of several alternatives applies them per field in its positive part instead.
     */
    private static String renderNegated(List<List<SearchTerm>> groups) {
        if (groups.size() != 1) {
            return MATCH_NOTHING;
        }
        StringJoiner negated = new StringJoiner(" | ");
        for (SearchTerm term : groups.get(0)) {
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
        if (mode == MatchMode.PREFIX && term.phrase() && !term.negated()) {
            return renderPrefixPhrase(term.text());
        }
        if (term.phrase() || term.negated() || mode == MatchMode.ANY || mode == MatchMode.ALL) {
            return escape(term.text());
        }
        String word = word(term.text());
        if (word == null) {
            return null;
        }
        // a reserved word such as near or about is escaped in braces inside fuzzy()
        if (mode == MatchMode.PREFIX) {
            return prefix(word);
        }
        return "fuzzy({" + word + "}" + FUZZY_SUFFIX;
    }

    /**
     * A phrase whose every word is a prefix, like PostgreSQL's prefix phrase, so the phrase being typed,
     * {@code "micronaut da}, finds "micronaut data": Oracle Text expands wildcards inside a phrase.
     */
    @Nullable
    private static String renderPrefixPhrase(String phrase) {
        StringJoiner words = new StringJoiner(" ");
        for (String part : phrase.trim().split("\\s+")) {
            String word = word(part);
            if (word != null) {
                words.add(prefix(word));
            }
        }
        return words.length() == 0 ? null : words.toString();
    }

    /** The letters and digits of a term; an underscore is Oracle Text's single-character wildcard, so it goes with the other symbols. */
    @Nullable
    private static String word(String text) {
        String word = text.replaceAll("[^\\p{L}\\p{N}]", "");
        return word.isEmpty() ? null : word;
    }

    /** A word expanded with {@code %}, or in braces when it is too short; braces do not combine with {@code %}. */
    private static String prefix(String word) {
        return word.length() < MIN_PREFIX_LENGTH ? "{" + word + "}" : word + "%";
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
     * {@link MatchMode#NATIVE} converter: the string is {@code CONTAINS} query syntax already.
     */
    @Singleton
    public static final class Native extends OracleTextQueryConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.NATIVE;
        }

        @Override
        protected Part part() {
            return Part.FULL;
        }
    }

    /**
     * The negated terms of a multi-field search, see {@link Part#NEGATED}.
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
