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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Converts LIKE patterns and wildcards to regular expressions for Nitrite queries.
 *
 * @since 5.2.0
 */
@Internal
public final class PatternConverter {

    private PatternConverter() { }

    /**
     * Resolves a regex pattern from the given value. Handles {@link Pattern} instances,
     * slash-delimited patterns, and wildcard (LIKE) patterns.
     *
     * @param resolved the raw pattern value
     * @return the resolved regex pattern string
     */
    public static String resolveRegexPattern(@Nullable Object resolved) {
        if (resolved == null) {
            return "";
        }
        if (resolved instanceof Pattern pattern) {
            return pattern.pattern();
        }
        String value = resolved.toString();
        String flags = "";
        if (value.startsWith("(?i)")) {
            flags = "(?i)";
            value = value.substring(4);
        }
        if (value.length() >= 2 && value.startsWith("/") && value.endsWith("/")) {
            return flags + value.substring(1, value.length() - 1);
        }
        if (looksLikeWildcardPattern(value)) {
            return flags + convertLikeToRegex(value);
        }
        return flags + value;
    }

    /**
     * Returns {@code true} if the value looks like a wildcard pattern
     * containing {@code %}, {@code _}, or {@code *} characters.
     *
     * @param value the value to inspect
     * @return {@code true} if the value contains wildcard characters
     */
    public static boolean looksLikeWildcardPattern(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith("^") || value.endsWith("$") || value.contains("[")
                || value.contains("(") || value.contains("|") || value.contains("\\")) {
            return false;
        }
        return value.indexOf('%') >= 0 || value.indexOf('_') >= 0 || value.indexOf('*') >= 0;
    }

    /**
     * Converts a LIKE pattern to a regex string using no escape character.
     *
     * @param pattern the LIKE pattern
     * @return the equivalent regex string
     */
    public static String convertLikeToRegex(String pattern) {
        return convertLikeToRegex(pattern, null);
    }

    /**
     * Converts a LIKE pattern to a regex string with an optional escape character.
     *
     * @param pattern the LIKE pattern
     * @param escapeChar the escape character, or {@code null} for none
     * @return the equivalent regex string
     */
    public static String convertLikeToRegex(String pattern, @Nullable Character escapeChar) {
        StringBuilder regex = new StringBuilder(pattern.length() + 6);
        if (pattern.isEmpty() || pattern.charAt(0) != '^') {
            regex.append('^');
        }
        boolean escaping = false;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (escaping) {
                appendLiteral(regex, ch);
                escaping = false;
            } else if (escapeChar != null && ch == escapeChar) {
                escaping = true;
            } else if (ch == '%') {
                regex.append(".*");
            } else if (ch == '_') {
                regex.append('.');
            } else if (ch == '*' && (i == 0 || pattern.charAt(i - 1) != '.')) {
                regex.append(".*");
            } else {
                regex.append(ch);
            }
        }
        if (escaping && escapeChar != null) {
            appendLiteral(regex, escapeChar);
        }
        if (regex.isEmpty() || regex.charAt(regex.length() - 1) != '$') {
            regex.append('$');
        }
        String converted = regex.toString();
        return converted.startsWith("(?s)") ? converted : "(?s)" + converted;
    }

    private static void appendLiteral(StringBuilder regex, char ch) {
        if ("\\.[]{}()+-^$?|*".indexOf(ch) >= 0) {
            regex.append('\\');
        }
        regex.append(ch);
    }
}
