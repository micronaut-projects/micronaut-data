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
package io.micronaut.data.age.operations;

import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps Cypher queries in the SQL that Apache AGE runs them with.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class AgeQueries {

    private static final Pattern ALIAS = Pattern.compile("(?is)^(.*\\S)\\s+AS\\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)$");
    private static final String DOLLAR_QUOTE = "$$";

    private AgeQueries() {
    }

    /**
     * Wraps a Cypher query in a call to {@code cypher()}.
     *
     * @param graph         The graph name
     * @param cypher        The Cypher query
     * @param hasParameters Whether the query takes a parameter map, bound to the single SQL parameter
     * @param columns       The number of columns the query returns
     * @return The SQL
     */
    static String wrap(String graph, String cypher, boolean hasParameters, int columns) {
        if (cypher.contains(DOLLAR_QUOTE)) {
            throw new IllegalArgumentException("A Cypher query for Apache AGE cannot contain $$");
        }
        if (!graph.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid Apache AGE graph name: " + graph);
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ag_catalog.cypher('")
            .append(graph).append("', $$ ").append(cypher).append(" $$");
        if (hasParameters) {
            sql.append(", ?");
        }
        sql.append(") AS (");
        int count = Math.max(columns, 1);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('c').append(i).append(" ag_catalog.agtype");
        }
        return sql.append(')').toString();
    }

    /**
     * The names of the columns a Cypher query returns: the alias of each item of its last {@code RETURN} clause,
     * or the expression itself when it has none.
     *
     * @param cypher The query
     * @return The column names, empty if the query returns nothing
     */
    static List<String> returnColumns(String cypher) {
        String masked = mask(cypher);
        String upper = masked.toUpperCase(Locale.ENGLISH);
        int returnIndex = lastTopLevelKeyword(upper, "RETURN");
        if (returnIndex < 0) {
            return List.of();
        }
        int start = returnIndex + "RETURN".length();
        int end = masked.length();
        for (String keyword : List.of("ORDER", "SKIP", "LIMIT", "UNION")) {
            int index = nextTopLevelKeyword(upper, keyword, start);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String items = cypher.substring(start, end).trim();
        if (items.toUpperCase(Locale.ENGLISH).startsWith("DISTINCT ")) {
            items = items.substring("DISTINCT ".length()).trim();
        }
        if (items.equals("*")) {
            throw new IllegalArgumentException("RETURN * is not supported with Apache AGE, name the returned columns");
        }
        List<String> columns = new ArrayList<>();
        for (String item : splitTopLevel(items, mask(items))) {
            String trimmed = item.trim();
            Matcher matcher = ALIAS.matcher(trimmed);
            if (matcher.matches()) {
                String alias = matcher.group(2);
                columns.add(alias.startsWith("`") ? alias.substring(1, alias.length() - 1) : alias);
            } else {
                columns.add(trimmed);
            }
        }
        return columns;
    }

    private static List<String> splitTopLevel(String items, String masked) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(items.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(items.substring(start));
        return parts;
    }

    private static int lastTopLevelKeyword(String upper, String keyword) {
        int found = -1;
        int index = nextTopLevelKeyword(upper, keyword, 0);
        while (index >= 0) {
            found = index;
            index = nextTopLevelKeyword(upper, keyword, index + keyword.length());
        }
        return found;
    }

    private static int nextTopLevelKeyword(String upper, String keyword, int from) {
        int depth = 0;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (i >= from && depth == 0 && upper.startsWith(keyword, i)
                && (i == 0 || !Character.isLetterOrDigit(upper.charAt(i - 1)))
                && (i + keyword.length() == upper.length() || !Character.isLetterOrDigit(upper.charAt(i + keyword.length())))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Replaces the content of string literals with spaces, keeping offsets.
     */
    private static String mask(String query) {
        StringBuilder sb = new StringBuilder(query.length());
        char quote = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < query.length()) {
                    sb.append("  ");
                    i++;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
