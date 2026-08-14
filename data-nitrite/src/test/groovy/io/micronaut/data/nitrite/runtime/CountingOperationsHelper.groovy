package io.micronaut.data.nitrite.runtime

import io.micronaut.core.beans.BeanProperty
import io.micronaut.data.model.Sort
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteCollection
import org.dizitart.no2.filters.Filter

/**
 * Test double for {@link NitriteOperationsHelper} that serves pre-registered collections and
 * records how often each one was looked up, so a test can assert the number of association
 * lookups a mapping performs.
 */
class CountingOperationsHelper implements NitriteOperationsHelper {

    private final Map<Class<?>, NitriteCollection> collections = [:]
    final Map<Class<?>, Integer> lookups = [:].withDefault { 0 }

    CountingOperationsHelper register(Class<?> type, NitriteCollection collection) {
        collections[type] = collection
        return this
    }

    int lookupCount(Class<?> type) {
        lookups[type]
    }

    @Override
    NitriteCollection getCollection(Class<?> type) {
        lookups[type] = lookups[type] + 1
        return collections[type]
    }

    @Override
    def <T> void generateIdIfNecessary(T entity, Class<T> type) {
    }

    @Override
    def <T> T updateEntityId(BeanProperty<T, Object> property, T entity, Object id) {
        return entity
    }

    @Override
    Object toFilterValue(Object value) {
        return value
    }

    @Override
    Sort parseSortFromHints(Map<String, Object> hints) {
        return null
    }

    @Override
    Sort parseSortFromJsonQuery(String query) {
        return null
    }

    @Override
    void logInsert(String collection, Object doc) {
    }

    @Override
    void logUpdate(String collection, Filter filter, Document update) {
    }

    @Override
    void logFind(String collection, Filter filter) {
    }

    @Override
    void logDelete(String collection, Filter filter) {
    }
}
