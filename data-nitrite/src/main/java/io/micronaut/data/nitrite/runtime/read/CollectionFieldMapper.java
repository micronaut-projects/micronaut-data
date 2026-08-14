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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the projected field name for native single-field projections
 * (e.g. findAgeByName()) from the query string or repository method name.
 *
 * @since 5.2.0
 */
@Internal
public final class CollectionFieldMapper {

    private static final Pattern COUNT_METHOD_PATTERN = Pattern.compile("^(find|get|read|list|search|query)(Count).*");
    private static final Pattern FIELD_BY_PATTERN = Pattern.compile("^(?:find|get|read|list|search|query)([A-Z][A-Za-z0-9]*)By");

    private final NitriteQueryParser queryParser;

    /**
     * Creates a new CollectionFieldMapper.
     *
     * @param queryParser the query parser
     */
    public CollectionFieldMapper(NitriteQueryParser queryParser) {
        this.queryParser = queryParser;
    }

    /**
     * Extract field name from query or method name.
     *
     * @param query the query string
     * @param methodName the method name
     * @return the field name, or null if not found
     */
    public @Nullable String extractFieldName(@Nullable String query, String methodName) {
        // First try to extract field from method name (most reliable for native projections)
        if (!COUNT_METHOD_PATTERN.matcher(methodName).matches()) {
            Matcher matcher = FIELD_BY_PATTERN.matcher(methodName);
            if (matcher.find()) {
                String fieldName = matcher.group(1);
                return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
            }
        }

        // Try $project field
        return queryParser.extractProjectionField(query);
    }
}
