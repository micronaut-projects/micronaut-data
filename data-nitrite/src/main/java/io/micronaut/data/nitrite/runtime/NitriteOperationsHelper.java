package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.Sort;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.Filter;

import java.util.Map;

/**
 * Interface for callbacks to DefaultNitriteRepositoryOperations.
 *
 * @since 4.14.0
 */
@Internal
public interface NitriteOperationsHelper {

    /**
     * Gets the Nitrite collection for the specified type.
     *
     * @param type the entity type
     * @return the Nitrite collection
     */
    NitriteCollection getCollection(Class<?> type);

    /**
     * Generates an ID for the entity if necessary.
     *
     * @param entity the entity
     * @param type the entity type
     * @param <T> the entity type
     */
    <T> void generateIdIfNecessary(T entity, Class<T> type);

    /**
     * Updates the entity ID property.
     *
     * @param property the ID property
     * @param entity the entity
     * @param id the ID value
     * @param <T> the entity type
     * @return the updated entity
     */
    <T> T updateEntityId(BeanProperty<T, Object> property, T entity, Object id);

    /**
     * Converts a value for use in a filter.
     *
     * @param value the value to convert
     * @return the converted value
     */
    @Nullable Object toFilterValue(@Nullable Object value);

    /**
     * Logs an insert operation.
     *
     * @param collection the collection name
     * @param doc the document being inserted
     */
    void logInsert(String collection, Object doc);

    /**
     * Logs an update operation.
     *
     * @param collection the collection name
     * @param filter the update filter
     * @param update the update document
     */
    void logUpdate(String collection, Filter filter, Document update);

    /**
     * Logs a find operation.
     *
     * @param collection the collection name
     * @param filter the find filter
     */
    void logFind(String collection, Filter filter);

    /**
     * Logs a delete operation.
     *
     * @param collection the collection name
     * @param filter the delete filter
     */
    void logDelete(String collection, Filter filter);

    /**
     * Parses a sort from a JSON query.
     *
     * @param queryString the JSON query string
     * @return the parsed sort
     */
    @Nullable Sort parseSortFromJsonQuery(@Nullable String queryString);

    /**
     * Parses a sort from query hints.
     *
     * @param hints the query hints
     * @return the parsed sort
     */
    @Nullable Sort parseSortFromHints(@Nullable Map<String, Object> hints);
}
