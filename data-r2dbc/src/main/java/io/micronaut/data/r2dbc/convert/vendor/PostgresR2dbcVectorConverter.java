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
package io.micronaut.data.r2dbc.convert.vendor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * VectorTypeConverter for Postgres R2DBC.
 * Persists Micronaut Vector instances as io.r2dbc.postgresql.codec.Vector values and converts to/from
 * pgvector codec for the POSTGRES dialect.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Requires(classes = io.r2dbc.postgresql.codec.Vector.class)
final class PostgresR2dbcVectorConverter extends AbstractR2dbcVectorConverter<io.r2dbc.postgresql.codec.Vector> {

    PostgresR2dbcVectorConverter(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public Class<io.r2dbc.postgresql.codec.Vector> getPersistedType() {
        return io.r2dbc.postgresql.codec.Vector.class;
    }

    @Override
    public Set<Class<? extends Vector>> supportedVectorTypes() {
        return Set.of(Vector.class, FloatVector.class);
    }

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.POSTGRES;
    }
}
