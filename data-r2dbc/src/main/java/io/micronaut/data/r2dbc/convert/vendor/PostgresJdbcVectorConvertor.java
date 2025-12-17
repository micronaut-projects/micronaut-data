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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * VectorTypeConvertor for Postgres R2DBC.
 * Persists Micronaut Vector instances as io.r2dbc.postgresql.codec.Vector values and converts to/from
 * pgvector codec for the POSTGRES dialect.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Named("POSTGRES")
@Requires(classes = io.r2dbc.postgresql.codec.Vector.class)
public class PostgresJdbcVectorConvertor implements VectorTypeConvertor<io.r2dbc.postgresql.codec.Vector> {

    private final ConversionService conversionService;

    public PostgresJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<io.r2dbc.postgresql.codec.Vector> getPersistedType() {
        return io.r2dbc.postgresql.codec.Vector.class;
    }

    @Override
    public io.r2dbc.postgresql.codec.Vector convert(Vector vector, Class<io.r2dbc.postgresql.codec.Vector> targetType) {
        if (vector.getClass().getName().equals(ByteVector.class.getName()) || vector.getClass().getName().equals(DoubleVector.class.getName())) {
            throw new IllegalArgumentException(getName() + " does not support " + targetType.getName());
        }
        return conversionService.convert(vector, targetType).orElse(null);
    }

    @Override
    public Vector convert(io.r2dbc.postgresql.codec.Vector object, Class<Vector> targetType) {
        if (targetType.getName().equals(ByteVector.class.getName()) || targetType.getName().equals(DoubleVector.class.getName())) {
            throw new IllegalArgumentException(getName() + " does not support " + targetType.getName());
        }
        return conversionService.convert(object, targetType).orElse(null);
    }

    @Override
    public Dialect getDialect() {
        return Dialect.POSTGRES;
    }

    @Override
    public @NonNull String getName() {
        return getDialect().toString();
    }
}
