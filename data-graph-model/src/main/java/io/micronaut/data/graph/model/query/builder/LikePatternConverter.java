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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Translates a {@code LIKE} pattern into the regular expression Cypher matches with {@code =~}: {@code %} matches
 * any sequence of characters, {@code _} any single character, and everything else is matched literally. Patterns
 * bound as method arguments are converted at bind time with the standard escape character {@code \}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
public final class LikePatternConverter implements AttributeConverter<String, String> {

    /**
     * The escape character of a {@code LIKE} pattern bound as a method argument.
     */
    public static final char DEFAULT_ESCAPE = '\\';

    private static final String REGEX_META_CHARACTERS = "\\.[]{}()<>*+-=!?^$|";

    @Override
    public @Nullable String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
        return entityValue == null ? null : toRegex(entityValue, DEFAULT_ESCAPE);
    }

    @Override
    public @Nullable String convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        return persistedValue;
    }

    /**
     * Translates a {@code LIKE} pattern into an anchored regular expression.
     *
     * @param pattern The pattern
     * @param escape  The escape character, or null if the pattern has none
     * @return The regular expression
     */
    public static String toRegex(String pattern, @Nullable Character escape) {
        StringBuilder regex = new StringBuilder(pattern.length() + 4).append('^');
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (escape != null && c == escape && i + 1 < pattern.length()) {
                appendLiteral(regex, pattern.charAt(++i));
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                appendLiteral(regex, c);
            }
        }
        return regex.append('$').toString();
    }

    private static void appendLiteral(StringBuilder regex, char c) {
        if (REGEX_META_CHARACTERS.indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }
}
