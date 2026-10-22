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
        List<SearchTerm> current = new ArrayList<>();
        if (query == null) {
            return groups;
        }
        int length = query.length();
        int i = 0;
        while (i < length) {
            char c = query.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            boolean negated = false;
            if (c == '-' && i + 1 < length && !Character.isWhitespace(query.charAt(i + 1))) {
                negated = true;
                i++;
                c = query.charAt(i);
            }
            if (c == '"') {
                int end = query.indexOf('"', i + 1);
                if (end < 0) {
                    end = length;
                }
                String phrase = query.substring(i + 1, end).trim();
                if (!phrase.isEmpty()) {
                    current.add(new SearchTerm(phrase, phrase.indexOf(' ') > 0, negated));
                }
                i = end + 1;
                continue;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(query.charAt(i))) {
                i++;
            }
            String word = query.substring(start, i);
            if (!negated && OR.equals(word)) {
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            current.add(new SearchTerm(word, false, negated));
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
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
}
