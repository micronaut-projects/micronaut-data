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
package io.micronaut.data.model.runtime.convert.vector.impl;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import io.micronaut.json.JsonMapper;

import java.util.Optional;

/**
 * String <-> Vector converters for textual storage (e.g. Oracle), using JsonMapper to parse "[...]".
 * Shared at runtime to avoid duplication across vendor layers; invalid JSON throws IllegalArgumentException.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Internal
@Requires(bean = JsonMapper.class)
final class VectorTextConvertersFactory {

    private final JsonMapper jsonMapper;

    VectorTextConvertersFactory(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Prototype
    DataTypeConverter<Vector, String> fromVectorToString() {
        return (vector, targetType, context) -> Optional.of(java.util.Arrays.toString(vector.toDoubleArray()));
    }

    @Prototype
    DataTypeConverter<DoubleVector, String> fromDoubleVectorToString() {
        return (vector, targetType, context) -> Optional.of(java.util.Arrays.toString(vector.toDoubleArray()));
    }

    @Prototype
    DataTypeConverter<FloatVector, String> fromFloatVectorToString() {
        return (vector, targetType, context) -> Optional.of(java.util.Arrays.toString(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<ByteVector, String> fromByteVectorToString() {
        return (vector, targetType, context) -> Optional.of(java.util.Arrays.toString(vector.toByteArray()));
    }

    @Prototype
    DataTypeConverter<String, Vector> fromStringToVector() {
        return (text, targetType, context) -> Optional.of(Vector.of(parseDoubleArray(text)));
    }

    @Prototype
    DataTypeConverter<String, DoubleVector> fromStringToDoubleVector() {
        return (text, targetType, context) -> Optional.of((DoubleVector) Vector.of(parseDoubleArray(text)));
    }

    @Prototype
    DataTypeConverter<String, FloatVector> fromStringToFloatVector() {
        return (text, targetType, context) -> Optional.of((FloatVector) Vector.of(parseFloatArray(text)));
    }

    @Prototype
    DataTypeConverter<String, ByteVector> fromStringToByteVector() {
        return (text, targetType, context) -> Optional.of((ByteVector) Vector.of(parseByteArray(text)));
    }

    // ========== Parsing helpers (require JsonMapper) ==========

    private double[] parseDoubleArray(@Nullable String txt) {
        if (txt == null) {
            return new double[0];
        }
        try {
            return jsonMapper.readValue(txt, double[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid vector JSON text (double[]): " + txt, e);
        }
    }

    private float[] parseFloatArray(@Nullable String txt) {
        if (txt == null) {
            return new float[0];
        }
        try {
            return jsonMapper.readValue(txt, float[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid vector JSON text (float[]): " + txt, e);
        }
    }

    private byte[] parseByteArray(@Nullable String txt) {
        if (txt == null) {
            return new byte[0];
        }
        try {
            return jsonMapper.readValue(txt, byte[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid vector JSON text (byte[]): " + txt, e);
        }
    }
}
