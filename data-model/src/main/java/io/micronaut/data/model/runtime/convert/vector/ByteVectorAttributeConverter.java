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
 * Attribute converter for Vector.ByteVector <-> byte[].
 */
@Singleton
public final class ByteVectorAttributeConverter implements AttributeConverter<Vector.ByteVector, byte[]> {

    @Override
    public @Nullable byte[] convertToPersistedValue(@Nullable Vector.ByteVector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        return entityValue.toByteArray();
    }

    @Override
    public @Nullable Vector.ByteVector convertToEntityValue(@Nullable byte[] persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        return (Vector.ByteVector) Vector.of(persistedValue);
    }

    @Override
    public Class<Vector.ByteVector> getEntityType() {
        return Vector.ByteVector.class;
    }

    @Override
    public Class<byte[]> getPersistedType() {
        return byte[].class;
    }
}
