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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import org.dizitart.no2.collection.Document;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for native single-field projections from Nitrite documents.
 * Used for methods like findAgeByName() that return a single field value.
 *
 * @since 5.0.0
 */
@Internal
public final class CollectionFieldMapper {

    private static final Pattern COUNT_METHOD_PATTERN = Pattern.compile("^(find|get|read|list|search|query)(Count).*");
    private static final Pattern FIELD_BY_PATTERN = Pattern.compile("^(?:find|get|read|list|search|query)([A-Z][A-Za-z0-9]*)By");

    private final NitriteQueryParser queryParser;
    private final ValueConverter valueConverter;
    private final NitriteEntityMapper entityMapper;

    /**
     * Creates a new CollectionFieldMapper.
     *
     * @param queryParser the query parser
     * @param valueConverter the value converter
     * @param entityMapper the entity mapper
     */
    public CollectionFieldMapper(NitriteQueryParser queryParser, ValueConverter valueConverter, NitriteEntityMapper entityMapper) {
        this.queryParser = queryParser;
        this.valueConverter = valueConverter;
        this.entityMapper = entityMapper;
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
        return project(doc, fieldName, null, resultType);
    }

    /**
     * Extract a single field value from a document.
     *
     * @param doc the document
     * @param fieldName the field name to extract
     * @param entity the entity metadata
     * @param resultType the result type
     * @param <R> the result type
     * @return the extracted value, or null if document or field is null
     */
    public <R> R project(Document doc, String fieldName, @Nullable RuntimePersistentEntity<?> entity, Class<R> resultType) {
        if (doc == null) {
            return null;
        }
        String normalized = entityMapper.normalizeFieldName(fieldName, entity);
        Object value = doc.get(normalized);
        if (value == null && !normalized.equals(fieldName)) {
            value = doc.get(fieldName);
        }
        return valueConverter.convert(value, resultType);
    }

    /**
     * Extract projected fields from a query and project the document.
     *
     * @param doc the document
     * @param query the query string (SQL or JSON)
     * @param methodName the method name
     * @param entity the entity metadata
     * @param resultType the result type
     * @param <R> the result type
     * @return the projected value, or null if no projection found
     */
    public <R> R project(Document doc, String query, String methodName, @Nullable RuntimePersistentEntity<?> entity, Class<R> resultType) {
        if (doc == null) {
            return null;
        }

        String fieldName = extractFieldName(query, methodName);
        if (fieldName != null) {
            return project(doc, fieldName, entity, resultType);
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
        if (!COUNT_METHOD_PATTERN.matcher(methodName).matches()) {
            Matcher matcher = FIELD_BY_PATTERN.matcher(methodName);
            if (matcher.find()) {
                String fieldName = matcher.group(1);
                return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
            }
        }

        // If not found in method name, try SELECT clause
        List<String> projectedFields = queryParser.parseSelectClause(query);
        if (projectedFields != null && projectedFields.size() == 1) {
            return projectedFields.getFirst();
        }

        // Try $project field
        return queryParser.extractProjectionField(query);
    }
}
