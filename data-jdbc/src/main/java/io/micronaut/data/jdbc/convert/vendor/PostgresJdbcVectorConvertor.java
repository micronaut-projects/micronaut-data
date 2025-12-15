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
package io.micronaut.data.jdbc.convert.vendor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL-specific {@link VectorTypeConvertor} that maps {@link Vector} to {@link PGobject} of type {@code vector}
 * and back.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Named("POSTGRES")
@Requires(classes = PGobject.class)
public class PostgresJdbcVectorConvertor implements VectorTypeConvertor<PGobject> {

    private final ConversionService conversionService;

    public PostgresJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<PGobject> getPersistedType() {
        return PGobject.class;
    }

    @Override
    public PGobject convert(Vector vector, Class<PGobject> targetType) {
        return conversionService.convert(vector, targetType).orElse(null);
    }

    @Override
    public Vector convert(PGobject object, Class<Vector> targetType) {
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
