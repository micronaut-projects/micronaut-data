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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.Vector;

import java.util.Optional;

/**
 * Shared helpers for Oracle VECTOR converters.
 * This class intentionally avoids any dependency on Oracle driver or Micronaut runtime types.
 *
 * Methods are protected static so vendor-specific factories can reuse them.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
public abstract class AbstractOracleTypeConvertersFactory {

    // ----------------------
    // Adapter-based array extraction helpers
    // ----------------------

    protected static Optional<double[]> vectorToDoubleArray(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case FLOAT64 -> Optional.of(adapter.toDoubleArray());
            case FLOAT32 -> Optional.of(toDouble(adapter.toFloatArray()));
            case BINARY, INT8 -> Optional.of(toDouble(adapter.toByteArray()));
        };
    }

    protected static Optional<float[]> vectorToFloatArray(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case FLOAT32 -> Optional.of(adapter.toFloatArray());
            case FLOAT64 -> Optional.of(toFloat(adapter.toDoubleArray()));
            case BINARY, INT8 -> Optional.of(toFloat(adapter.toByteArray()));
        };
    }

    protected static Optional<byte[]> vectorToByteArray(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case BINARY, INT8 -> Optional.of(adapter.toByteArray());
            case FLOAT32 -> Optional.of(toByte(adapter.toFloatArray()));
            case FLOAT64 -> Optional.of(toByte(adapter.toDoubleArray()));
        };
    }

    // ----------------------
    // Adapter to Vector helper
    // ----------------------

    protected static Vector toVector(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case FLOAT32 -> Vector.of(adapter.toFloatArray());
            case FLOAT64 -> Vector.of(adapter.toDoubleArray());
            case BINARY, INT8 -> Vector.of(adapter.toByteArray());
        };
    }

    // ----------------------
    // Array conversion helpers
    // ----------------------

    protected static double[] toDouble(float[] f) {
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = f[i];
        }
        return out;
    }

    protected static double[] toDouble(byte[] b) {
        double[] out = new double[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    protected static float[] toFloat(double[] d) {
        float[] out = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (float) d[i];
        }
        return out;
    }

    protected static float[] toFloat(byte[] b) {
        float[] out = new float[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    protected static byte[] toByte(float[] f) {
        byte[] out = new byte[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (byte) f[i];
        }
        return out;
    }

    protected static byte[] toByte(double[] d) {
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (byte) d[i];
        }
        return out;
    }

    // ----------------------
    // Inner types (must be last for Checkstyle)
    // ----------------------

    /**
     * Neutral adapter for Oracle VECTOR to avoid driver dependencies in shared helpers.
     */
    public enum OracleVectorKind {
        FLOAT32, FLOAT64, INT8, BINARY
    }

    /**
     * Adapter API that concrete factories use to map oracle.sql.VECTOR to a neutral shape.
     */
    public interface OracleVectorAdapter {
        OracleVectorKind getKind();

        float[] toFloatArray();

        double[] toDoubleArray();

        byte[] toByteArray();
    }
}
