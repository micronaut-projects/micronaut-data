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
import io.micronaut.core.annotation.Nullable;

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
    // String parsing helpers (Oracle textual format e.g. "[1.0, 2.0]")
    // ----------------------

    protected static String trimBrackets(@Nullable String txt) {
        if (txt == null) {
            return "";
        }
        String s = txt.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    protected static double[] parseDoubleArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new double[0];
        }
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }

    protected static float[] parseFloatArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new float[0];
        }
        String[] parts = s.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    protected static int[] parseIntArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new int[0];
        }
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            double d = Double.parseDouble(parts[i].trim());
            long r = Math.round(d);
            if (r > Integer.MAX_VALUE) {
                r = Integer.MAX_VALUE;
            }
            if (r < Integer.MIN_VALUE) {
                r = Integer.MIN_VALUE;
            }
            out[i] = (int) r;
        }
        return out;
    }

    protected static byte[] parseByteArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new byte[0];
        }
        String[] parts = s.split(",");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            double d = Double.parseDouble(parts[i].trim());
            int r = (int) Math.round(d);
            if (r > Byte.MAX_VALUE) {
                r = Byte.MAX_VALUE;
            }
            if (r < Byte.MIN_VALUE) {
                r = Byte.MIN_VALUE;
            }
            out[i] = (byte) r;
        }
        return out;
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

    protected static double[] toDouble(int[] ints) {
        double[] out = new double[ints.length];
        for (int i = 0; i < ints.length; i++) {
            out[i] = ints[i];
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

    protected static float[] toFloat(int[] ints) {
        float[] out = new float[ints.length];
        for (int i = 0; i < ints.length; i++) {
            out[i] = ints[i];
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

    protected static int[] toInt(float[] f) {
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (int) f[i];
        }
        return out;
    }

    protected static int[] toInt(double[] d) {
        int[] out = new int[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (int) d[i];
        }
        return out;
    }

    protected static int[] toInt(byte[] b) {
        int[] out = new int[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    protected static byte[] toByte(int[] ints) {
        byte[] out = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            out[i] = (byte) ints[i];
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
}
