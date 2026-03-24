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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import org.dizitart.no2.collection.Document;

import java.util.List;

/**
 * Strategy for native single-field projections from Nitrite documents.
 * Used for methods like findAgeByName() that return a single field value.
 *
 * @since 5.0.0
 */
@Internal
public final class CollectionFieldMapper {

    private final NitriteQueryParser queryParser;
    private final ValueConverter valueConverter;

    /**
     * Creates a new CollectionFieldMapper.
     *
     * @param queryParser the query parser
     * @param valueConverter the value converter
     */
    public CollectionFieldMapper(NitriteQueryParser queryParser, ValueConverter valueConverter) {
        this.queryParser = queryParser;
        this.valueConverter = valueConverter;
    }

    /**
     * Extract a single field value from a document.
     *
     * @param doc the document
     * @param fieldName the field name to extract
     * @param resultType the result type
     * @param <R> the result type
     * @return the extracted value, or null if document or field is null
     */
    public <R> R project(Document doc, String fieldName, Class<R> resultType) {
        if (doc == null) {
            return null;
        }
        Object value = doc.get(fieldName);
        return valueConverter.convert(value, resultType);
    }

    /**
     * Extract projected fields from a query and project the document.
     *
     * @param doc the document
     * @param query the query string (SQL or JSON)
     * @param methodName the method name
     * @param resultType the result type
     * @param <R> the result type
     * @return the projected value, or null if no projection found
     */
    public <R> R project(Document doc, String query, String methodName, Class<R> resultType) {
        if (doc == null) {
            return null;
        }

        String fieldName = extractFieldName(query, methodName);
        if (fieldName != null) {
            Object value = doc.get(fieldName);
            return valueConverter.convert(value, resultType);
        }

        return null;
    }

    /**
     * Extract field name from query or method name.
     *
     * @param query the query string
     * @param methodName the method name
     * @return the field name, or null if not found
     */
    public String extractFieldName(String query, String methodName) {
        // First try to extract field from method name (most reliable for native projections)
        if (!methodName.matches("^(find|get|read|list|search|query)(Count).*")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(?:find|get|read|list|search|query)([A-Z][a-z0-9]+)By");
            java.util.regex.Matcher matcher = pattern.matcher(methodName);
            if (matcher.find()) {
                String fieldName = matcher.group(1);
                return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
            }
        }

        // If not found in method name, try SELECT clause
        List<String> projectedFields = queryParser.parseSelectClause(query);
        if (projectedFields != null && projectedFields.size() == 1) {
            return projectedFields.get(0);
        }

        // Try $project field
        String projectField = queryParser.extractProjectionField(query);
        if (projectField != null) {
            return projectField;
        }

        return null;
    }
}
