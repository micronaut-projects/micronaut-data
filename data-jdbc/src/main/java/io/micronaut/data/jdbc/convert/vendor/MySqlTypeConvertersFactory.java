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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * MySQL HeatWave VECTOR converters (JDBC).
 *
 * IMPORTANT: The MySQL driver rejects textual binding (String) for VECTOR columns.
 * Binding must use primitive arrays. These converters map between Micronaut Vector types
 * and primitive arrays for JDBC binding/reading paths.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = com.mysql.cj.jdbc.Driver.class)
@Internal
final class MySqlTypeConvertersFactory {

    @Prototype
    DataTypeConverter<Vector, byte[]> vectorToBinary() {
        return (vector, targetType, context) -> {
            if (!(vector instanceof FloatVector)) {
                throw new IllegalArgumentException("MYSQL does not support " + vector.getClass().getName());
            }
            return Optional.of(encodeFloatArray(vector.toFloatArray()));
        };
    }

    @Prototype
    DataTypeConverter<FloatVector, byte[]> floatVectorToBinary() {
        return (vector, targetType, context) -> Optional.of(encodeFloatArray(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<byte[], Vector> binaryToVector() {
        return (bytes, targetType, context) -> Optional.of(Vector.of(decodeFloatArray(bytes)));
    }

    @Prototype
    DataTypeConverter<byte[], FloatVector> binaryToFloatVector() {
        return (bytes, targetType, context) -> Optional.of(decodeFloatVector(bytes));
    }

    private static byte[] encodeFloatArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private static float[] decodeFloatArray(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Invalid MySQL VECTOR binary length " + bytes.length
                + ": expected a multiple of " + Float.BYTES);
        }
        int floatCount = bytes.length / Float.BYTES;
        float[] floats = new float[floatCount];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < floatCount; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    private static FloatVector decodeFloatVector(byte[] bytes) {
        Vector vector = Vector.of(decodeFloatArray(bytes));
        if (vector instanceof FloatVector floatVector) {
            return floatVector;
        }
        throw new IllegalStateException("Expected FloatVector from float[] decoding but got " + vector.getClass().getName());
    }
}
