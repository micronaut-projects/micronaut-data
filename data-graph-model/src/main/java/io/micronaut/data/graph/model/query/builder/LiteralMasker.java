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

/**
 * Masks the string literals and comments of a query, replacing their content with spaces so that the offsets of
 * everything else are kept, before the query is scanned for keywords and parameters.
 *
 * <p>In Cypher a quote inside a literal is escaped with a backslash and a line comment opens with {@code //}.
 * In SQL and PGQL a quote is doubled instead, and a line comment opens with {@code --} as well. Block comments
 * open with {@code /*} in both.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class LiteralMasker {

    private final String query;
    private final boolean sql;
    private final StringBuilder out;
    private int position;
    private char quote;
    private boolean lineComment;
    private boolean blockComment;

    private LiteralMasker(String query, boolean sql) {
        this.query = query;
        this.sql = sql;
        this.out = new StringBuilder(query.length());
    }

    /**
     * Masks a Cypher query.
     *
     * @param query The query
     * @return The query with the content of its literals and comments replaced by spaces
     */
    public static String mask(String query) {
        return mask(query, false);
    }

    /**
     * Masks a query.
     *
     * @param query The query
     * @param sql   Whether the query is SQL or PGQL, where {@code --} opens a comment and a quote is doubled rather
     *              than escaped with a backslash, instead of Cypher
     * @return The query with the content of its literals and comments replaced by spaces
     */
    public static String mask(String query, boolean sql) {
        return new LiteralMasker(query, sql).mask();
    }

    private String mask() {
        while (position < query.length()) {
            char c = query.charAt(position);
            char next = position + 1 < query.length() ? query.charAt(position + 1) : 0;
            if (lineComment) {
                maskLineComment(c);
            } else if (blockComment) {
                maskBlockComment(c, next);
            } else if (quote != 0) {
                maskQuoted(c, next);
            } else {
                scan(c, next);
            }
        }
        return out.toString();
    }

    private void maskLineComment(char c) {
        if (c == '\n') {
            lineComment = false;
            append(c);
        } else {
            append(' ');
        }
    }

    private void maskBlockComment(char c, char next) {
        if (c == '*' && next == '/') {
            blockComment = false;
            appendTwo("  ");
        } else {
            append(' ');
        }
    }

    private void maskQuoted(char c, char next) {
        if (c == '\\' && next != 0 && !sql) {
            appendTwo("  ");
        } else if (c == quote) {
            quote = 0;
            append(c);
        } else {
            append(' ');
        }
    }

    private void scan(char c, char next) {
        if (c == '\'' || c == '"' || c == '`') {
            quote = c;
            append(c);
        } else if ((c == '/' && next == '/') || (sql && c == '-' && next == '-')) {
            lineComment = true;
            appendTwo("  ");
        } else if (c == '/' && next == '*') {
            blockComment = true;
            appendTwo("  ");
        } else {
            append(c);
        }
    }

    private void append(char c) {
        out.append(c);
        position++;
    }

    private void appendTwo(String text) {
        out.append(text);
        position += 2;
    }
}
