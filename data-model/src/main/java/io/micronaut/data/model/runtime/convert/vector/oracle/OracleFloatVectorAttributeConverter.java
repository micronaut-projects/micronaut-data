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
import io.micronaut.data.model.runtime.convert.vector.FloatVectorAttributeConverter;
import jakarta.inject.Singleton;
import oracle.sql.VECTOR;

import java.util.Arrays;

/**
 * Attribute converter for Vector.FloatVector <-> float[].
 */
@Singleton
@Requires(classes = VECTOR.class)
public final class OracleFloatVectorAttributeConverter implements OracleVectorAttributeConverterToString<Vector.FloatVector, float[]>, FloatVectorAttributeConverter<float[]> {

    @Override
    public @Nullable float[] convertToPersistedValue(@Nullable Vector.FloatVector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        return entityValue.toFloatArray();
    }

    @Override
    public @Nullable Vector.FloatVector convertToEntityValue(@Nullable float[] persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        return (Vector.FloatVector) Vector.of(persistedValue);
    }

    @Override
    public Class<float[]> getPersistedType() {
        return float[].class;
    }

    @Override
    public String convertToString(float[] value) {
        return Arrays.toString(value);
    }
}
