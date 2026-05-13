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
package io.micronaut.data.model.vector;

import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.vector.FloatVectorAttributeConverter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Float-backed immutable vector.
 * Split from Vector.java into a dedicated package.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 * @param data the backing float values array (a defensive copy is returned by accessors)
 */
@TypeDef(type = DataType.OBJECT, converter = FloatVectorAttributeConverter.class)
public record FloatVector(float[] data) implements Vector {


    /**
     * Creates a float vector.
     *
     * @param data the backing values
     */
    public FloatVector {
        Objects.requireNonNull(data, "FloatVector data must not be null");
        data = Arrays.copyOf(data, data.length);
    }

    /**
     * @return a copy of the vector values
     */
    @Override
    public float[] data() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Class<? extends Number> getType() {
        return Float.TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] toFloatArray() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] toDoubleArray() {
        return VectorArrays.toDoubleArray(data.length, i -> data[i]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toByteArray() {
        return VectorArrays.toByteArray(data.length, i -> data[i]);
    }

    @Override
    public @NonNull String toString() {
        return "F" + Arrays.toString(data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FloatVector(float[] data1))) {
            return false;
        }
        return Arrays.equals(this.data, data1);
    }
}
