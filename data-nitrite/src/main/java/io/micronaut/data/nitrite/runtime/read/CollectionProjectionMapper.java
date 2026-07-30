package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.ValueConverter;
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
     * @param entity the entity metadata
     * @param resultType the expected result type
     * @param isDto whether this is a DTO projection
     * @param <R> the result type
     * @return list of projected values
     */
    public <R> List<R> mapResults(RecordStream<Document> cursor, List<String> fields, @Nullable RuntimePersistentEntity<?> entity, Class<R> resultType, boolean isDto) {
        List<R> results = new ArrayList<>();
        for (Document doc : cursor) {
            R result = mapDocument(doc, fields, entity, resultType, isDto);
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
     * @param entity the entity metadata
     * @param resultType the expected result type
     * @param isDto whether this is a DTO projection
     * @param <R> the result type
     * @return the mapped result, or null if not applicable
     */
    @SuppressWarnings("unchecked")
    public <R> @Nullable R mapDocument(@Nullable Document doc, List<String> fields, @Nullable RuntimePersistentEntity<?> entity, Class<R> resultType, boolean isDto) {
        if (doc == null) {
            return null;
        }

        if (fields.size() == 1) {
            // Single field projection - extract and convert the value
            Object value = getProjectedValue(doc, fields.getFirst(), entity);
            return valueConverter.convert(value, resultType);
        } else if (Object[].class.equals(resultType)) {
            Object[] values = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                values[i] = getProjectedValue(doc, fields.get(i), entity);
            }
            return (R) values;
        } else if (isDto) {
            // DTO projection with multiple fields - use Micronaut introspection-based mapping
            return entityMapper.fromDocument(doc, resultType);
        } else {
            // Multi-field native projection - return Document (rare case, for backwards compatibility)
            return (R) doc;
        }
    }

    private Object getProjectedValue(Document doc, String fieldName, @Nullable RuntimePersistentEntity<?> entity) {
        String normalized = entityMapper.normalizeFieldName(fieldName, entity);
        Object value = doc.get(normalized);
        if (value == null && !normalized.equals(fieldName)) {
            value = doc.get(fieldName);
        }
        return value;
    }
}
