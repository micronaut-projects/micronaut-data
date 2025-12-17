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

import io.micronaut.core.naming.Named;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;

/**
 * Dialect-specific converter for vector values to and from the persisted JDBC type.
 *
 * @param <T> The persisted JDBC type for a given dialect
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface VectorTypeConvertor<T> extends Named {

    /**
     * Returns the persisted (driver) type used for the given dialect.
     * <p>Examples: Oracle may use {@code String} textual representation; Postgres R2DBC uses
     * {@code io.r2dbc.postgresql.codec.Vector}; JDBC may use driver-specific objects.</p>
     *
     * @return the persisted type handled by this converter
     * @since 5.0.0
     */
    Class<T> getPersistedType();

    /**
     * Convert an entity-side {@link Vector} into the dialect-specific persisted type.
     *
     * @param vector the vector value from the entity side
     * @param targetType the target persisted type class (same as {@link #getPersistedType()})
     * @return the persisted value to bind to JDBC/R2DBC
     * @since 5.0.0
     */
    T convert(Vector vector, Class<T> targetType);

    /**
     * Convert a dialect-specific persisted value into the entity-side {@link Vector}.
     *
     * @param object the persisted value (type returned by {@link #getPersistedType()})
     * @param targetType the target entity type (typically {@code Vector.class})
     * @return the entity-side vector value
     * @since 5.0.0
     */
    Vector convert(T object, Class<Vector> targetType);

    /**
     * The SQL dialect this converter targets.
     *
     * @return the dialect
     * @since 5.0.0
     */
    Dialect getDialect();

}
