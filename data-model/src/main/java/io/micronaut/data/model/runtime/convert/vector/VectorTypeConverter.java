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
package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.vector.SparseVector;
import io.micronaut.data.model.vector.Vector;

import java.util.Set;

/**
 * Dialect-specific converter for vector values to and from the persisted database type (JDBC/R2DBC).
 *
 * Responsibilities:
 * - Provide the exact persisted driver type via {@link #getPersistedType()} (e.g. PGvector, String, byte[]).
 * - Declare the supported vector subtypes via {@link #supportedVectorTypes()} (keep this list minimal).
 * - Identify the target database via {@link #databaseType()}.
 *
 * Selection semantics:
 * - At runtime, Micronaut Data selects a converter based on the current {@link io.micronaut.data.model.runtime.convert.DatabaseType}.
 * - Implementations should avoid overlapping support for the same database type; ambiguous configurations may result in a selection error.
 * - For read paths, converters may be chosen using both the database type and the actual persisted type (see {@link #getPersistedType()}).
 *
 * @param <T> The persisted driver type for a given dialect (e.g. org.postgresql.util.PGobject/PGvector, String, byte[])
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface VectorTypeConverter<T> {

    /**
     * Convert an entity-side {@link Vector} into the dialect-specific persisted type.
     *
     * Contract:
     * - The supplied vector should be an instance of one of {@link #supportedVectorTypes()} or a compatible superclass.
     * - The returned object MUST be an instance of {@link #getPersistedType()}.
     *
     * @param vector the vector value from the entity side (non-null)
     * @return the persisted value to bind to JDBC/R2DBC (non-null)
     */
    T convert(Vector vector);

     /**
      * Convert a dialect-specific persisted value into the entity-side {@link Vector}.
      *
      * Contract:
      * - The supplied object SHOULD be an instance of {@link #getPersistedType()} (or assignable).
      * - The {@code targetType} will be one of the supported vector types or {@code Vector.class}.
      * - Converters SHOULD perform the necessary numeric conversions (e.g. float/double narrowing) consistently.
      *
      * @param object the persisted value (type returned by {@link #getPersistedType()}, non-null)
      * @param targetType the target entity type (typically {@code Vector.class} or a concrete subtype)
      * @return the entity-side vector value (non-null)
      */
     Vector convert(T object, Class<Vector> targetType);

    /**
     * The exact vector subtypes supported by this converter for the declared database type.
     * Keep this set minimal to avoid ambiguity (e.g. prefer a single, precise subtype, or {@code Vector.class} if generic).
     *
     * @return the set of supported vector types (non-empty)
     */
    Set<Class<? extends Vector>> supportedVectorTypes();

    /**
     * The database type (dialect family) this converter targets.
     *
     * @return the database type
     */
    DatabaseType databaseType();

    /**
     * The exact persisted driver type this converter reads/writes.
     * Examples: {@code org.postgresql.util.PGobject} or {@code com.pgvector.PGvector} for PostgreSQL,
     * {@code String} for Oracle textual representation, {@code byte[]} for MySQL HeatWave binary.
     *
     * @return the persisted driver type class
     */
    Class<T> getPersistedType();

    /**
     * Whether this converter is designed for sparse vectors.
     *
     * Used as a tiebreaker when multiple converters exist for the same database type.
     * Implementations that handle {@link SparseVector} persisted paths should return {@code true}.
     *
     * @return {@code true} if sparse vectors are supported/preferred by this converter
     */
    default boolean isSparseSupported() {
        return false;
    }
}
