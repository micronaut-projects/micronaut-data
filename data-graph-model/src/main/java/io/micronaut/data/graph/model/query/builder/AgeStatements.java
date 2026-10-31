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
package io.micronaut.data.graph.model.query.builder;

import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The SQL statements that run Cypher on Apache AGE, built when repositories are compiled: a call to {@code cypher()}
 * whose result columns are named after the columns the Cypher query returns. The graph name is the token
 * {@link #GRAPH_TOKEN}, replaced with the configured graph when the statement runs.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class AgeStatements {

    /**
     * Stands for the graph name in a statement until it runs.
     */
    public static final String GRAPH_TOKEN = "$__graph";

    /**
     * Separates the Cypher query from the end of a statement.
     */
    public static final String CYPHER_END = " $$";

    private static final String CYPHER_START = "', $$ ";
    private static final String PARAMETER_ARGUMENT = "$$, ?)";
    private static final Pattern ALIAS = Pattern.compile("(?is)^(.*\\S)\\s+AS\\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)$");
    private static final Pattern PARAMETER = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern GRAPH_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String DOLLAR_QUOTE = "$$";
    private static final String RESULT_COLUMN = "result";

    private AgeStatements() {
    }

    /**
     * Wraps a Cypher query in a call to {@code cypher()}. The statement takes the parameter map as its single SQL
     * parameter when the query refers to parameters.
     *
     * @param cypher The Cypher query
     * @return The SQL statement
     * @throws IllegalArgumentException if Apache AGE cannot run the query
     */
    public static String wrap(String cypher) {
        if (cypher.contains(DOLLAR_QUOTE)) {
            throw new IllegalArgumentException("A Cypher query for Apache AGE cannot contain $$");
        }
        List<String> columns = returnColumns(cypher);
        StringBuilder sql = new StringBuilder("SELECT * FROM ag_catalog.cypher('")
            .append(GRAPH_TOKEN).append(CYPHER_START).append(cypher).append(CYPHER_END);
        if (hasParameters(cypher)) {
            sql.append(", ?");
        }
        sql.append(") AS (");
        if (columns.isEmpty()) {
            columns = List.of(RESULT_COLUMN);
        }
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('"').append(columns.get(i).replace("\"", "\"\"")).append("\" ag_catalog.agtype");
        }
        return sql.append(')').toString();
    }

    /**
     * @param statement A statement built by {@link #wrap(String)}
     * @return Whether the statement takes the parameter map
     */
    public static boolean takesParameters(String statement) {
        return statement.contains(PARAMETER_ARGUMENT);
    }

    /**
     * @param statement A statement built by {@link #wrap(String)}
     * @return The offset of the Cypher query in the statement, or -1 if it is not a statement
     */
    public static int cypherStart(String statement) {
        int index = statement.indexOf(CYPHER_START);
        return index < 0 ? -1 : index + CYPHER_START.length();
    }

    /**
     * @param statement A statement built by {@link #wrap(String)}
     * @return The offset just after the Cypher query in the statement
     */
    public static int cypherEnd(String statement) {
        return statement.lastIndexOf(CYPHER_END);
    }

    /**
     * Replaces the graph token of a statement.
     *
     * @param statement A statement built by {@link #wrap(String)}
     * @param graph     The graph name
     * @return The statement to run
     */
    public static String forGraph(String statement, String graph) {
        return statement.replace(GRAPH_TOKEN, graph);
    }

    /**
     * Checks a graph name can be written into a statement.
     *
     * @param graph The graph name
     * @return The graph name
     * @throws IllegalArgumentException if the name is not a valid identifier
     */
    public static String validateGraphName(String graph) {
        if (!GRAPH_NAME.matcher(graph).matches()) {
            throw new IllegalArgumentException("Invalid Apache AGE graph name: " + graph);
        }
        return graph;
    }

    private static boolean hasParameters(String cypher) {
        Matcher matcher = PARAMETER.matcher(mask(cypher));
        while (matcher.find()) {
            // Tokens such as the path limit are replaced before the statement runs
            if (!matcher.group(1).startsWith("__")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The names of the columns a Cypher query returns: the alias of each item of its last {@code RETURN} clause,
     * or the expression itself when it has none.
     *
     * @param cypher The query
     * @return The column names, empty if the query returns nothing
     */
    private static List<String> returnColumns(String cypher) {
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
                && (i == 0 || !isNameCharacter(upper.charAt(i - 1)))
                && (i + keyword.length() == upper.length() || !isNameCharacter(upper.charAt(i + keyword.length())))
                && !isAlias(upper, i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A property access such as {@code n.limit} or a parameter such as {@code $skip} is a name, not a clause.
     */
    private static boolean isNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$' || c == '`';
    }

    /**
     * A word after {@code AS} is an alias, not a clause, as in {@code RETURN count(n) AS limit}.
     */
    private static boolean isAlias(String upper, int index) {
        int i = index - 1;
        while (i >= 0 && Character.isWhitespace(upper.charAt(i))) {
            i--;
        }
        return i >= 1 && upper.charAt(i) == 'S' && upper.charAt(i - 1) == 'A'
            && (i < 2 || !isNameCharacter(upper.charAt(i - 2)));
    }

    private static String mask(String query) {
        return LiteralMasker.mask(query);
    }
}
