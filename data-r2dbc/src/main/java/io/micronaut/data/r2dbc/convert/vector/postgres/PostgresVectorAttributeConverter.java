/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.r2dbc.convert.vector.postgres;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.Vector;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorAttributeConverter;
import io.r2dbc.postgresql.codec.Codec;
import jakarta.inject.Singleton;

/**
 * Attribute converter for Vector <-> io.r2dbc.postgresql.codec.Vector (pgvector).
 *
 * Stores a float[] as a pgvector textual value like: "[1.0,2.5,-3.75]"
 * and parses the same textual value back into a float[] on load.
 *
 * @since 5.0
 */
@Singleton
@Requires(classes = Codec.class)
public final class PostgresVectorAttributeConverter implements VectorAttributeConverter<io.r2dbc.postgresql.codec.Vector> {

    @Override
    public @Nullable io.r2dbc.postgresql.codec.Vector convertToPersistedValue(@Nullable Vector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        return io.r2dbc.postgresql.codec.Vector.of(entityValue.toFloatArray());
    }

    @Override
    public @Nullable Vector convertToEntityValue(@Nullable io.r2dbc.postgresql.codec.Vector persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        String txt = persistedValue.toString();
        float[] values = parsePgVectorText(txt);
        return Vector.of(values);
    }

    @Override
    public Class<io.r2dbc.postgresql.codec.Vector> getPersistedType() {
        return io.r2dbc.postgresql.codec.Vector.class;
    }

    @Override
    public Dialect getDialect() {
        return Dialect.POSTGRES;
    }

    /**
     * Parse pgvector textual value like: [1.0,2,3.5].
     */
    private static float[] parsePgVectorText(String txt) {
        if (txt == null) {
            return new float[0];
        }
        String s = txt.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            return new float[0];
        }
        String[] parts = s.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

}
