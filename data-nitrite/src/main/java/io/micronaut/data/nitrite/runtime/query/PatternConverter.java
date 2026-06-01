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
import java.util.regex.Pattern;

@Internal
final class PatternConverter {

    private PatternConverter() {}

    static String resolveRegexPattern(Object resolved) {
        if (resolved == null) return "";
        if (resolved instanceof Pattern pattern) return pattern.pattern();
        String value = resolved.toString();
        if (value.length() >= 2 && value.startsWith("/") && value.endsWith("/")) {
            return value.substring(1, value.length() - 1);
        }
        if (looksLikeWildcardPattern(value)) return convertLikeToRegex(value);
        return value;
    }

    static boolean looksLikeWildcardPattern(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.startsWith("^") || value.endsWith("$") || value.contains("[")
                || value.contains("(") || value.contains("|") || value.contains("\\")) {
            return false;
        }
        return value.indexOf('%') >= 0 || value.indexOf('_') >= 0 || value.indexOf('*') >= 0;
    }

    static String convertLikeToRegex(String pattern) {
        StringBuilder regex = new StringBuilder(pattern.length() + 6);
        if (pattern.isEmpty() || pattern.charAt(0) != '^') regex.append('^');
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '%') {
                regex.append(".*");
            } else if (ch == '_') {
                regex.append('.');
            } else if (ch == '*' && (i == 0 || pattern.charAt(i - 1) != '.')) {
                regex.append(".*");
            } else {
                regex.append(ch);
            }
        }
        if (regex.isEmpty() || regex.charAt(regex.length() - 1) != '$') regex.append('$');
        String converted = regex.toString();
        return converted.startsWith("(?s)") ? converted : "(?s)" + converted;
    }
}
