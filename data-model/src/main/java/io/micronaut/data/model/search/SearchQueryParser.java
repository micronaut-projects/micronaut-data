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

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the portable search grammar shared by every backend.
 *
 * <p>The grammar is the one users know from web search boxes and from GORM's {@code search(String)}:</p>
 * <ul>
 *     <li>terms are separated by whitespace,</li>
 *     <li>{@code "quoted words"} form a phrase,</li>
 *     <li>a leading {@code -} negates a term or phrase,</li>
 *     <li>the word {@code OR} separates alternatives; terms not separated by {@code OR} belong to the
 *     same group.</li>
 * </ul>
 *
 * <p>The result is a disjunction of groups, each group a list of terms. {@code bake coffee -cake OR tea}
 * parses to {@code [[bake, coffee, -cake], [tea]]}. How the terms of a group are combined (any term,
 * all terms) is up to the {@link MatchMode}.</p>
 *
 * @since 5.4.0
 */
@Experimental
public final class SearchQueryParser {

    private static final String OR = "OR";

    /**
     * The token {@link #readTerm} returns for an {@code OR} separating two groups.
     */
    private static final SearchTerm GROUP_SEPARATOR = new SearchTerm(OR, false, false);

    private SearchQueryParser() {
    }

    /**
     * Parses a search string.
     *
     * @param query The search string
     * @return The groups of terms; never empty groups, possibly an empty list
     */
    public static List<List<SearchTerm>> parse(@Nullable String query) {
        List<List<SearchTerm>> groups = new ArrayList<>();
        if (query == null) {
            return groups;
        }
        List<SearchTerm> current = new ArrayList<>();
        Cursor cursor = new Cursor(query);
        while (cursor.skipWhitespace()) {
            SearchTerm term = readTerm(cursor);
            if (term == GROUP_SEPARATOR) {
                // an OR closes the current group
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            } else if (term != null) {
                current.add(term);
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    /**
     * Reads the next term, phrase or {@code OR}.
     *
     * @param cursor The cursor, on a non-whitespace character
     * @return The term, {@link #GROUP_SEPARATOR} for an {@code OR}, or {@code null} for an empty phrase
     */
    @Nullable
    private static SearchTerm readTerm(Cursor cursor) {
        boolean negated = cursor.consumeNegation();
        if (cursor.peek() == '"') {
            return cursor.readPhrase(negated);
        }
        String word = cursor.readWord();
        return !negated && OR.equals(word) ? GROUP_SEPARATOR : new SearchTerm(word, false, negated);
    }

    /**
     * Parses a search string into a flat list of terms, ignoring {@code OR} grouping.
     *
     * @param query The search string
     * @return The terms
     */
    public static List<SearchTerm> terms(@Nullable String query) {
        List<SearchTerm> terms = new ArrayList<>();
        for (List<SearchTerm> group : parse(query)) {
            terms.addAll(group);
        }
        return terms;
    }

    /**
     * A position in the search string.
     */
    private static final class Cursor {
        private final String query;
        private int position;

        Cursor(String query) {
            this.query = query;
        }

        /**
         * @return Whether a character follows the skipped whitespace
         */
        boolean skipWhitespace() {
            while (position < query.length() && Character.isWhitespace(query.charAt(position))) {
                position++;
            }
            return position < query.length();
        }

        char peek() {
            return query.charAt(position);
        }

        /**
         * Consumes a {@code -} directly followed by a term.
         */
        boolean consumeNegation() {
            if (peek() == '-' && position + 1 < query.length() && !Character.isWhitespace(query.charAt(position + 1))) {
                position++;
                return true;
            }
            return false;
        }

        /**
         * Reads a quoted phrase, to the closing quote or the end of the string.
         *
         * @return The term, or {@code null} for an empty phrase
         */
        @Nullable
        SearchTerm readPhrase(boolean negated) {
            int end = query.indexOf('"', position + 1);
            if (end < 0) {
                end = query.length();
            }
            String phrase = query.substring(position + 1, end).trim();
            position = end + 1;
            return phrase.isEmpty() ? null : new SearchTerm(phrase, phrase.contains(" "), negated);
        }

        String readWord() {
            int start = position;
            while (position < query.length() && !Character.isWhitespace(query.charAt(position))) {
                position++;
            }
            return query.substring(start, position);
        }
    }
}
