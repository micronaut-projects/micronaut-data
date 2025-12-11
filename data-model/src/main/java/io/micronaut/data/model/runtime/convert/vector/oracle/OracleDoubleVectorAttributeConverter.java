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
package io.micronaut.data.model.runtime.convert.vector.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.Vector;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.DoubleVectorAttributeConverter;
import jakarta.inject.Singleton;
import oracle.sql.VECTOR;

import java.util.Arrays;

/**
 * Attribute converter for Vector.DoubleVector <-> double[].
 *
 * This enables mapping entity fields of type {@link Vector.DoubleVector}
 * to the persisted driver-friendly primitive array double[] and back.
 */
@Singleton
@Requires(classes = VECTOR.class)
public final class OracleDoubleVectorAttributeConverter implements OracleVectorAttributeConverterToString<Vector.DoubleVector, double[]>, DoubleVectorAttributeConverter<double[]> {

    @Override
    public @Nullable double[] convertToPersistedValue(@Nullable Vector.DoubleVector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        return entityValue.toDoubleArray();
    }

    @Override
    public @Nullable Vector.DoubleVector convertToEntityValue(@Nullable double[] persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        return (Vector.DoubleVector) Vector.of(persistedValue);
    }

    @Override
    public Class<double[]> getPersistedType() {
        return double[].class;
    }

    @Override
    public String convertToString(double[] value) {
        return Arrays.toString(value);
    }

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }
}
