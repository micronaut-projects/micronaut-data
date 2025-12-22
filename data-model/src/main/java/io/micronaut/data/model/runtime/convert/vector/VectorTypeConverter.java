/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.vector.Vector;

import java.util.List;

/**
 * Dialect-specific converter for vector values to and from the persisted database type (JDBC/R2DBC).
 *
 * @param <T> The persisted JDBC type for a given dialect
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface VectorTypeConverter<T> {

    /**
     * Convert an entity-side {@link Vector} into the dialect-specific persisted type.
     *
     * @param vector the vector value from the entity side
     * @return the persisted value to bind to JDBC/R2DBC
     * @since 5.0.0
     */
    T convert(Vector vector);

     /**
      * Convert a dialect-specific persisted value into the entity-side {@link Vector}.
      *
      * @param object the persisted value (type returned by {@link #getPersistedType()})
      * @param targetType the target entity type (typically {@code Vector.class})
      * @return the entity-side vector value
      * @since 5.0.0
      */
     Vector convert(T object, Class<Vector> targetType);

    List<Class<? extends Vector>> supportedVectorTypes();

    DatabaseType databaseType();

    Class<T> getPersistedType();
}
