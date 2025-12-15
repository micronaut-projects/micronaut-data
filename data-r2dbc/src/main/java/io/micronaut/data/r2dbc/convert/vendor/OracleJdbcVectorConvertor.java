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
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import oracle.sql.VECTOR;

/**
 * VectorTypeConvertor for Oracle R2DBC.
 * Persists Micronaut Vector instances as String values and converts to/from
 * Oracle textual vector representation for the ORACLE dialect.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
@Singleton
@Named("ORACLE")
@Requires(classes = VECTOR.class)
public class OracleJdbcVectorConvertor implements VectorTypeConvertor<String> {

    private final ConversionService conversionService;

    public OracleJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<String> getPersistedType() {
        return String.class;
    }

    @Override
    public String convert(Vector vector, Class<String> targetType) {
        return conversionService.convert(vector, targetType).orElse(null);
    }

    @Override
    public Vector convert(String object, Class<Vector> targetType) {
        return conversionService.convert(object, targetType).get();
    }

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public @NonNull String getName() {
        return getDialect().toString();
    }
}
