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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.Type;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import oracle.r2dbc.OracleR2dbcTypes;
import oracle.sql.VECTOR;
import org.jspecify.annotations.Nullable;

@Internal
@Singleton
@Requires(classes = {VECTOR.class, OracleR2dbcTypes.class})
final class OracleR2dbcVectorBindSupport implements VectorBindSupport {

    private static final Type ORACLE_VECTOR_TYPE = OracleR2dbcTypes.VECTOR;

    private final ConversionService conversionService;

    OracleR2dbcVectorBindSupport() {
        this(ConversionService.SHARED);
    }

    @Inject
    OracleR2dbcVectorBindSupport(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public @Nullable Parameter toTypedVectorParameter(@Nullable Object value, @Nullable String query) {
        if (value instanceof CharSequence) {
            throw new IllegalArgumentException("String VECTOR literals are not supported. Use io.micronaut.data.model.vector.Vector instead.");
        }
        if (!(value instanceof Vector vector)) {
            return null;
        }
        VECTOR oracleVector = conversionService.convert(vector, VECTOR.class)
            .orElseThrow(() -> new IllegalArgumentException("Cannot convert " + vector.getClass().getName() + " to oracle.sql.VECTOR"));
        return Parameters.in(ORACLE_VECTOR_TYPE, oracleVector);
    }

}
