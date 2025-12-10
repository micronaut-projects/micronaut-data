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
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.Vector;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Micronaut TypeConverter beans for converting Vector.* <-> primitive arrays.
 * These cover non-entity parameters (e.g. @Query method parameters) so that the
 * general QueryStatement.convertRequired(..) path can convert values without ad-hoc unwrapping.
 *
 * Note: Entity property conversion can leverage AttributeConverters implemented separately.
 */
final class VectorTypeConverters {

    private VectorTypeConverters() {
    }

    // Double
    @Singleton
    static final class DoubleVectorToDoubleArray implements TypeConverter<Vector.DoubleVector, double[]> {
        @Override
        public @NonNull Optional<double[]> convert(@Nullable Vector.DoubleVector object, @NonNull Class<double[]> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : object.toDoubleArray());
        }
    }

    @Singleton
    static final class DoubleArrayToDoubleVector implements TypeConverter<double[], Vector.DoubleVector> {
        @Override
        public @NonNull Optional<Vector.DoubleVector> convert(@Nullable double[] object, @NonNull Class<Vector.DoubleVector> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : (Vector.DoubleVector) Vector.of(object));
        }
    }

    // Float
    @Singleton
    static final class FloatVectorToFloatArray implements TypeConverter<Vector.FloatVector, float[]> {
        @Override
        public @NonNull Optional<float[]> convert(@Nullable Vector.FloatVector object, @NonNull Class<float[]> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : object.toFloatArray());
        }
    }

    @Singleton
    static final class FloatArrayToFloatVector implements TypeConverter<float[], Vector.FloatVector> {
        @Override
        public @NonNull Optional<Vector.FloatVector> convert(@Nullable float[] object, @NonNull Class<Vector.FloatVector> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : (Vector.FloatVector) Vector.of(object));
        }
    }

    // Int
    @Singleton
    static final class IntVectorToIntArray implements TypeConverter<Vector.IntVector, int[]> {
        @Override
        public @NonNull Optional<int[]> convert(@Nullable Vector.IntVector object, @NonNull Class<int[]> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : object.toIntegerArray());
        }
    }

    @Singleton
    static final class IntArrayToIntVector implements TypeConverter<int[], Vector.IntVector> {
        @Override
        public @NonNull Optional<Vector.IntVector> convert(@Nullable int[] object, @NonNull Class<Vector.IntVector> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : (Vector.IntVector) Vector.of(object));
        }
    }

    // Byte
    @Singleton
    static final class ByteVectorToByteArray implements TypeConverter<Vector.ByteVector, byte[]> {
        @Override
        public @NonNull Optional<byte[]> convert(@Nullable Vector.ByteVector object, @NonNull Class<byte[]> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : object.toByteArray());
        }
    }

    @Singleton
    static final class ByteArrayToByteVector implements TypeConverter<byte[], Vector.ByteVector> {
        @Override
        public @NonNull Optional<Vector.ByteVector> convert(@Nullable byte[] object, @NonNull Class<Vector.ByteVector> targetType, @NonNull ConversionContext context) {
            return Optional.ofNullable(object == null ? null : (Vector.ByteVector) Vector.of(object));
        }
    }
}
