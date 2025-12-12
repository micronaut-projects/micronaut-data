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
package io.micronaut.data.jdbc.convert.vector.postgres;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.convert.JdbcConversionContext;
import io.micronaut.data.model.Vector;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorAttributeConverter;
import jakarta.inject.Singleton;
import org.postgresql.util.PGobject;

/**
 * Attribute converter for Vector <-> double[].
 *
 * This enables mapping entity fields of type {@link Vector}
 * to/from a PostgreSQL pgvector value.
 *
 * @since 5.0
 */
@Singleton
@Requires(classes = PGobject.class)
@Requires(bean = JdbcConversionContext.class)
public final class PostgresVectorAttributeConverter implements VectorAttributeConverter<PGobject> {

    @Override
    public @Nullable PGobject convertToPersistedValue(@Nullable Vector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        // Vector#toDoubleArray already returns a defensive copy
        return toPgVector(entityValue.toDoubleArray());
    }

    @Override
    public @Nullable Vector convertToEntityValue(@Nullable PGobject persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        if (!"vector".equalsIgnoreCase(persistedValue.getType()) && !"halfvec".equalsIgnoreCase(persistedValue.getType())) {
            return null;
        }
        String txt = persistedValue.getValue();
        double[] values = parsePgVectorText(txt);
        return Vector.of(values);
    }

    @Override
    public Class<PGobject> getPersistedType() {
        return PGobject.class;
    }

    @Override
    public Dialect getDialect() {
        return Dialect.POSTGRES;
    }

    /**
     * Parse pgvector textual value like: [1.0, 2, 3.5].
     */
    private static double[] parsePgVectorText(String txt) {
        if (txt == null) {
            return new double[0];
        }
        String s = txt.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            return new double[0];
        }
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }

       private static PGobject toPgVector(double[] values) {
        try {
            PGobject obj = new PGobject();
            obj.setType("vector");
            obj.setValue(formatPgVector(values));
            return obj;
        } catch (Exception e) {
            throw new DataAccessException("Cannot create PGobject for pgvector: " + e.getMessage(), e);
        }
    }

    /**
     * Format a double array into pgvector text format: [v1, v2, v3].
     */
    private static String formatPgVector(double[] values) {
        StringBuilder sb = new StringBuilder(values.length * 4 + 2);
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            // Default string form is accepted by pgvector
            sb.append(Double.toString(values[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
