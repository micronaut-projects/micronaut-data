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
package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.Vector;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

/**
 * Attribute converter for Vector.IntVector <-> int[].
 */
@Singleton
public final class IntVectorAttributeConverter implements AttributeConverter<Vector.IntVector, int[]> {

    @Override
    public @Nullable int[] convertToPersistedValue(@Nullable Vector.IntVector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        return entityValue.toIntegerArray();
    }

    @Override
    public @Nullable Vector.IntVector convertToEntityValue(@Nullable int[] persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        return (Vector.IntVector) Vector.of(persistedValue);
    }

    @Override
    public Class<Vector.IntVector> getEntityType() {
        return Vector.IntVector.class;
    }

    @Override
    public Class<int[]> getPersistedType() {
        return int[].class;
    }
}
