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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Definition;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.IntVectorAttributeConverter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Int-backed immutable vector.
 * Split from Vector.java into a dedicated package.
 *
 * @since 4.7
 */
@TypeDef(type = DataType.OBJECT, converter = IntVectorAttributeConverter.class, definition = "vector", definitions = {
    @Definition(value = "VECTOR", dialect = Dialect.ORACLE, format = "VECTOR(%d,INT8)"),
    @Definition(value = "vector", dialect = Dialect.POSTGRES, format = "vector(%d)")
})
public final class IntVector implements Vector {

    private final int[] data;

    public IntVector(int[] data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    @Override
    public @NonNull Class<? extends Number> getType() {
        return Integer.TYPE;
    }

    @Override
    public @NonNull float[] toFloatArray() {
        float[] out = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return out;
    }

    @Override
    public @NonNull double[] toDoubleArray() {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return out;
    }

    @Override
    public @NonNull byte[] toByteArray() {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) data[i];
        }
        return out;
    }

    @Override
    public @NonNull int[] toIntegerArray() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public String toString() {
        return "I" + Arrays.toString(data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IntVector other)) {
            return false;
        }
        return Arrays.equals(this.data, other.data);
    }
}
