package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.collection.Document;

/**
 * Centralized entity and DTO mapping for Nitrite operations.
 * Handles both full entity loading and DTO projections using
 * Micronaut's introspection-based entity mapper.
 *
 * @since 5.0.0
 */
@Internal
public final class ObjectRepositoryMapper {

    private final NitriteEntityMapper entityMapper;

    /**
     * Creates a new ObjectRepositoryMapper.
     *
     * @param entityMapper the entity mapper
     */
    public ObjectRepositoryMapper(NitriteEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
    }

    /**
     * Load a full entity from a document.
     * Used when the result type equals the root entity type (no projection).
     *
     * @param doc the document
     * @param entityType the entity type
     * @param <T> the entity type
     * @return the loaded entity, or null if document is null
     */
    public <T> @Nullable T loadEntity(@Nullable Document doc, Class<T> entityType) {
        if (doc == null) {
            return null;
        }
        return entityMapper.fromDocument(doc, entityType);
    }
}
