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
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.common.RecordStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized projection mapping for Nitrite operations.
 * Handles single-field, multi-field, and DTO projections from document cursors.
 *
 * @since 5.0.0
 */
@Internal
public final class CollectionProjectionMapper {

    private final ValueConverter valueConverter;
    private final NitriteEntityMapper entityMapper;

    /**
     * Creates a new CollectionProjectionMapper.
     *
     * @param valueConverter the value converter
     * @param entityMapper the entity mapper
     */
    public CollectionProjectionMapper(ValueConverter valueConverter, NitriteEntityMapper entityMapper) {
        this.valueConverter = valueConverter;
        this.entityMapper = entityMapper;
    }

    /**
     * Extract projected results from a cursor.
     *
     * @param cursor the projected cursor
     * @param fields the projected field names
     * @param resultType the expected result type
     * @param isDto whether this is a DTO projection
     * @param <R> the result type
     * @return list of projected values
     */
    public <R> List<R> mapResults(RecordStream<Document> cursor, List<String> fields, Class<R> resultType, boolean isDto) {
        List<R> results = new ArrayList<>();
        for (Document doc : cursor) {
            R result = mapDocument(doc, fields, resultType, isDto);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Map a single document to a projected result.
     *
     * @param doc the document
     * @param fields the projected field names
     * @param resultType the expected result type
     * @param isDto whether this is a DTO projection
     * @param <R> the result type
     * @return the mapped result, or null if not applicable
     */
    @SuppressWarnings("unchecked")
    public <R> R mapDocument(Document doc, List<String> fields, Class<R> resultType, boolean isDto) {
        if (doc == null) {
            return null;
        }

        if (fields.size() == 1) {
            // Single field projection - extract and convert the value
            Object value = doc.get(fields.get(0));
            return valueConverter.convert(value, resultType);
        } else if (isDto) {
            // DTO projection with multiple fields - use Micronaut introspection-based mapping
            return (R) entityMapper.fromDocument(doc, resultType);
        } else {
            // Multi-field native projection - return Document (rare case, for backwards compatibility)
            return (R) doc;
        }
    }

    /**
     * Map a single document to a single projected value.
     *
     * @param doc the document
     * @param fieldName the projected field name
     * @param resultType the expected result type
     * @param <R> the result type
     * @return the mapped value, or null if document or field is null
     */
    public <R> R mapSingleField(Document doc, String fieldName, Class<R> resultType) {
        if (doc == null) {
            return null;
        }
        Object value = doc.get(fieldName);
        return valueConverter.convert(value, resultType);
    }
}
