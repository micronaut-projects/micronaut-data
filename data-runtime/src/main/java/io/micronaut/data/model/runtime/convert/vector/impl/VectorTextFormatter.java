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

import java.util.Arrays;

@Internal
public final class VectorTextFormatter {

    private VectorTextFormatter() {
    }

    public static String toText(Vector vector) {
        return toText(vector, false);
    }

    public static String toText(Vector vector, boolean sparse) {
        if (sparse) {
            if (vector.getType() == Byte.TYPE) {
                return toOracleSparseText(vector.toByteArray());
            }
            if (vector.getType() == Float.TYPE) {
                return toOracleSparseText(vector.toFloatArray());
            }
            return toOracleSparseText(vector.toDoubleArray());
        }
        if (vector.getType() == Float.TYPE) {
            return Arrays.toString(vector.toFloatArray());
        }
        if (vector.getType() == Byte.TYPE) {
            return Arrays.toString(vector.toByteArray());
        }
        return Arrays.toString(vector.toDoubleArray());
    }

    private static String toOracleSparseText(double[] values) {
        StringBuilder indexes = new StringBuilder();
        StringBuilder nonZeroValues = new StringBuilder();
        indexes.append('[');
        nonZeroValues.append('[');
        boolean first = true;
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            if (value == 0d) {
                continue;
            }
            if (!first) {
                indexes.append(',');
                nonZeroValues.append(',');
            }
            indexes.append(i);
            nonZeroValues.append(value);
            first = false;
        }
        indexes.append(']');
        nonZeroValues.append(']');
        return "[" + values.length + "," + indexes + "," + nonZeroValues + "]";
    }

    private static String toOracleSparseText(float[] values) {
        StringBuilder indexes = new StringBuilder();
        StringBuilder nonZeroValues = new StringBuilder();
        indexes.append('[');
        nonZeroValues.append('[');
        boolean first = true;
        for (int i = 0; i < values.length; i++) {
            float value = values[i];
            if (value == 0f) {
                continue;
            }
            if (!first) {
                indexes.append(',');
                nonZeroValues.append(',');
            }
            indexes.append(i);
            nonZeroValues.append(value);
            first = false;
        }
        indexes.append(']');
        nonZeroValues.append(']');
        return "[" + values.length + "," + indexes + "," + nonZeroValues + "]";
    }

    private static String toOracleSparseText(byte[] values) {
        StringBuilder indexes = new StringBuilder();
        StringBuilder nonZeroValues = new StringBuilder();
        indexes.append('[');
        nonZeroValues.append('[');
        boolean first = true;
        for (int i = 0; i < values.length; i++) {
            byte value = values[i];
            if (value == 0) {
                continue;
            }
            if (!first) {
                indexes.append(',');
                nonZeroValues.append(',');
            }
            indexes.append(i);
            nonZeroValues.append(value);
            first = false;
        }
        indexes.append(']');
        nonZeroValues.append(']');
        return "[" + values.length + "," + indexes + "," + nonZeroValues + "]";
    }
}
