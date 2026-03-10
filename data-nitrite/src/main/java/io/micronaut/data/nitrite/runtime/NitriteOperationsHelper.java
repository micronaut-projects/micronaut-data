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
import io.micronaut.core.beans.BeanProperty;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.Filter;

/**
 * Interface for callbacks to DefaultNitriteRepositoryOperations.
 *
 * @since 4.14.0
 */
@Internal
public interface NitriteOperationsHelper {

    NitriteCollection getCollection(Class<?> type);

    <T> void generateIdIfNecessary(T entity, Class<T> type);

    <T> T updateEntityId(BeanProperty<T, Object> property, T entity, Object id);

    Object toFilterValue(Object value);

    void logInsert(String collection, Object doc);

    void logUpdate(String collection, Filter filter, Document update);

    void logFind(String collection, Filter filter);
}
