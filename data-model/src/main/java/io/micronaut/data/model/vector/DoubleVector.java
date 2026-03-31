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
import io.micronaut.data.model.runtime.convert.vector.DoubleVectorAttributeConverter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Double-backed immutable vector.
 * Split from Vector.java into a dedicated package.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 * @param data the backing double values array (a defensive copy is returned by accessors)
 */
@TypeDef(type = DataType.OBJECT, converter = DoubleVectorAttributeConverter.class)
public record DoubleVector(double[] data) implements Vector {


    /**
     * Creates a double vector.
     *
     * @param data the backing values
     */
    public DoubleVector {
        Objects.requireNonNull(data, "DoubleVector data must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Class<? extends Number> getType() {
        return Double.TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] toFloatArray() {
        float[] out = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (float) data[i];
        }
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] toDoubleArray() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toByteArray() {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) data[i];
        }
        return out;
    }

    @Override
    public @NonNull String toString() {
        return "D" + Arrays.toString(data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DoubleVector(double[] data1))) {
            return false;
        }
        return Arrays.equals(this.data, data1);
    }
}
