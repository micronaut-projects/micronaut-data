/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.vector.BinaryVectorAttributeConverter;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bit-packed immutable vector, mapped to Oracle {@code VECTOR(n, BINARY)} columns.
 *
 * <p>Each dimension is one bit. The backing array packs eight dimensions per byte, most significant bit
 * first, so a vector of {@code n} dimensions holds {@code n / 8} bytes and the number of dimensions is
 * always a multiple of 8. This is the layout Oracle uses for {@code BINARY} vectors.</p>
 *
 * <p>Compare binary vectors with {@link io.micronaut.data.model.vector.search.ScoringFunction#HAMMING}
 * or {@link io.micronaut.data.model.vector.search.ScoringFunction#JACCARD}.</p>
 *
 * <p>The array conversions of {@link Vector} return one element per dimension, {@code 0} or {@code 1}.
 * Use {@link #data()} for the packed bytes.</p>
 *
 * @param data the packed bits, eight dimensions per byte, most significant bit first (a defensive copy is returned by accessors)
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
@TypeDef(type = DataType.OBJECT, converter = BinaryVectorAttributeConverter.class)
public record BinaryVector(byte[] data) implements Vector {

    /**
     * Creates a binary vector from packed bits.
     *
     * @param data the packed bits, eight dimensions per byte, most significant bit first
     */
    public BinaryVector {
        Objects.requireNonNull(data, "BinaryVector data must not be null");
        data = Arrays.copyOf(data, data.length);
    }

    /**
     * Creates a binary vector with one dimension per bit.
     *
     * @param bits the bits; the length must be a multiple of 8
     * @return the binary vector
     */
    public static @NonNull BinaryVector of(boolean... bits) {
        Objects.requireNonNull(bits, VALUES);
        if (bits.length % Byte.SIZE != 0) {
            throw new IllegalArgumentException("The number of bits of a binary vector must be a multiple of 8, but was " + bits.length);
        }
        byte[] packed = new byte[bits.length / Byte.SIZE];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                packed[i / Byte.SIZE] |= (byte) (0x80 >>> (i % Byte.SIZE));
            }
        }
        return new BinaryVector(packed);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * @return the number of dimensions, which is the number of bits
     */
    public int dimensions() {
        return data.length * Byte.SIZE;
    }

    /**
     * Returns the bit at the given dimension.
     *
     * @param index the dimension, from {@code 0}
     * @return {@code true} when the bit is set
     */
    public boolean get(int index) {
        Objects.checkIndex(index, dimensions());
        return ((data[index / Byte.SIZE] & 0xff) & (0x80 >>> (index % Byte.SIZE))) != 0;
    }

    /**
     * @return one element per dimension, {@code true} where the bit is set
     */
    public boolean[] toBooleanArray() {
        boolean[] bits = new boolean[dimensions()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = get(i);
        }
        return bits;
    }

    /**
     * Returns {@code byte}: the type of the packed backing array.
     *
     * @return {@link Byte#TYPE}
     */
    @Override
    public @NonNull Class<? extends Number> getType() {
        return Byte.TYPE;
    }

    @Override
    public float[] toFloatArray() {
        return VectorArrays.toFloatArray(dimensions(), i -> get(i) ? 1 : 0);
    }

    @Override
    public double[] toDoubleArray() {
        return VectorArrays.toDoubleArray(dimensions(), i -> get(i) ? 1 : 0);
    }

    @Override
    public byte[] toByteArray() {
        byte[] values = new byte[dimensions()];
        for (int i = 0; i < values.length; i++) {
            values[i] = get(i) ? (byte) 1 : (byte) 0;
        }
        return values;
    }

    @Override
    public @NonNull String toString() {
        StringBuilder builder = new StringBuilder(dimensions() + 1).append('b');
        for (int i = 0; i < dimensions(); i++) {
            builder.append(get(i) ? '1' : '0');
        }
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BinaryVector(byte[] data1))) {
            return false;
        }
        return Arrays.equals(this.data, data1);
    }
}
