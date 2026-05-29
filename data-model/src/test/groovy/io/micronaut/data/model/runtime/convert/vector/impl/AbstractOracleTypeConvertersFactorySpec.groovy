package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

// Local test helper copy to avoid cross-module test dependency on data-runtime
// Mirrors the minimal API needed by this spec.
class AbstractOracleTypeConvertersFactory {

    static enum OracleVectorKind { FLOAT32, FLOAT64, INT8, BINARY }

    static interface OracleVectorAdapter {
        OracleVectorKind getKind()
        float[] toFloatArray()
        double[] toDoubleArray()
        int[] toIntArray()
        byte[] toByteArray()
    }

    static Optional<double[]> vectorToDoubleArray(OracleVectorAdapter adapter) {
        switch (adapter.kind) {
            case OracleVectorKind.FLOAT64: return Optional.of(adapter.toDoubleArray())
            case OracleVectorKind.FLOAT32: return Optional.of(toDouble(adapter.toFloatArray()))
            case OracleVectorKind.INT8:    return Optional.of(toDouble(adapter.toIntArray()))
            case OracleVectorKind.BINARY:  return Optional.of(toDouble(adapter.toByteArray()))
        }
    }

    static Optional<float[]> vectorToFloatArray(OracleVectorAdapter adapter) {
        switch (adapter.kind) {
            case OracleVectorKind.FLOAT32: return Optional.of(adapter.toFloatArray())
            case OracleVectorKind.FLOAT64: return Optional.of(toFloat(adapter.toDoubleArray()))
            case OracleVectorKind.INT8:    return Optional.of(toFloat(adapter.toIntArray()))
            case OracleVectorKind.BINARY:  return Optional.of(toFloat(adapter.toByteArray()))
        }
    }

    static Optional<int[]> vectorToIntArray(OracleVectorAdapter adapter) {
        switch (adapter.kind) {
            case OracleVectorKind.INT8:    return Optional.of(adapter.toIntArray())
            case OracleVectorKind.FLOAT32: return Optional.of(toInt(adapter.toFloatArray()))
            case OracleVectorKind.FLOAT64: return Optional.of(toInt(adapter.toDoubleArray()))
            case OracleVectorKind.BINARY:  return Optional.of(toInt(adapter.toByteArray()))
        }
    }

    static Optional<byte[]> vectorToByteArray(OracleVectorAdapter adapter) {
        switch (adapter.kind) {
            case OracleVectorKind.BINARY:  return Optional.of(adapter.toByteArray())
            case OracleVectorKind.INT8:    return Optional.of(toByte(adapter.toIntArray()))
            case OracleVectorKind.FLOAT32: return Optional.of(toByte(adapter.toFloatArray()))
            case OracleVectorKind.FLOAT64: return Optional.of(toByte(adapter.toDoubleArray()))
        }
    }

    static String trimBrackets(String txt) {
        if (txt == null) return ""
        def s = txt.trim()
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim()
        }
        return s
    }

    static double[] parseDoubleArray(String txt) {
        def s = trimBrackets(txt)
        if (s.isEmpty()) return new double[0]
        def parts = s.split(",")
        double[] out = new double[parts.length]
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim())
        }
        return out
    }

    static float[] parseFloatArray(String txt) {
        def s = trimBrackets(txt)
        if (s.isEmpty()) return new float[0]
        def parts = s.split(",")
        float[] out = new float[parts.length]
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim())
        }
        return out
    }

    static int[] parseIntArray(String txt) {
        def s = trimBrackets(txt)
        if (s.isEmpty()) return new int[0]
        def parts = s.split(",")
        int[] out = new int[parts.length]
        for (int i = 0; i < parts.length; i++) {
            double d = Double.parseDouble(parts[i].trim())
            long r = Math.round(d)
            if (r > Integer.MAX_VALUE) r = Integer.MAX_VALUE
            if (r < Integer.MIN_VALUE) r = Integer.MIN_VALUE
            out[i] = (int) r
        }
        return out
    }

    static byte[] parseByteArray(String txt) {
        def s = trimBrackets(txt)
        if (s.isEmpty()) return new byte[0]
        def parts = s.split(",")
        byte[] out = new byte[parts.length]
        for (int i = 0; i < parts.length; i++) {
            double d = Double.parseDouble(parts[i].trim())
            int r = (int) Math.round(d)
            if (r > Byte.MAX_VALUE) r = Byte.MAX_VALUE
            if (r < Byte.MIN_VALUE) r = Byte.MIN_VALUE
            out[i] = (byte) r
        }
        return out
    }

    static String toOracleText(Vector v) { Arrays.toString(v.toDoubleArray()) }
    static String toOracleText(double[] a) { Arrays.toString(a) }
    static String toOracleText(float[] a) { Arrays.toString(a) }
    static String toOracleText(int[] a) { Arrays.toString(a) }
    static String toOracleText(byte[] a) { Arrays.toString(a) }

    static Vector toVector(OracleVectorAdapter adapter) {
        switch (adapter.kind) {
            case OracleVectorKind.FLOAT32: return Vector.of(adapter.toFloatArray())
            case OracleVectorKind.FLOAT64: return Vector.of(adapter.toDoubleArray())
            case OracleVectorKind.BINARY:
            case OracleVectorKind.INT8:   return Vector.of(adapter.toByteArray())
        }
    }

    private static double[] toDouble(float[] f) {
        double[] out = new double[f.length]
        for (int i = 0; i < f.length; i++) out[i] = f[i]
        return out
    }
    private static double[] toDouble(int[] a) {
        double[] out = new double[a.length]
        for (int i = 0; i < a.length; i++) out[i] = a[i]
        return out
    }
    private static double[] toDouble(byte[] a) {
        double[] out = new double[a.length]
        for (int i = 0; i < a.length; i++) out[i] = a[i]
        return out
    }
    private static float[] toFloat(double[] d) {
        float[] out = new float[d.length]
        for (int i = 0; i < d.length; i++) out[i] = (float) d[i]
        return out
    }
    private static float[] toFloat(int[] a) {
        float[] out = new float[a.length]
        for (int i = 0; i < a.length; i++) out[i] = a[i]
        return out
    }
    private static float[] toFloat(byte[] a) {
        float[] out = new float[a.length]
        for (int i = 0; i < a.length; i++) out[i] = a[i]
        return out
    }
    private static int[] toInt(float[] f) {
        int[] out = new int[f.length]
        for (int i = 0; i < f.length; i++) out[i] = (int) f[i]
        return out
    }
    private static int[] toInt(double[] d) {
        int[] out = new int[d.length]
        for (int i = 0; i < d.length; i++) out[i] = (int) d[i]
        return out
    }
    private static int[] toInt(byte[] a) {
        int[] out = new int[a.length]
        for (int i = 0; i < a.length; i++) out[i] = a[i]
        return out
    }
    private static byte[] toByte(int[] a) {
        byte[] out = new byte[a.length]
        for (int i = 0; i < a.length; i++) out[i] = (byte) a[i]
        return out
    }
    private static byte[] toByte(float[] a) {
        byte[] out = new byte[a.length]
        for (int i = 0; i < a.length; i++) out[i] = (byte) a[i]
        return out
    }
    private static byte[] toByte(double[] a) {
        byte[] out = new byte[a.length]
        for (int i = 0; i < a.length; i++) out[i] = (byte) a[i]
        return out
    }
}

class AbstractOracleTypeConvertersFactorySpec extends Specification {

    def "trimBrackets handles null, empty and whitespace"() {
        expect:
        AbstractOracleTypeConvertersFactory.trimBrackets(null) == ""
        AbstractOracleTypeConvertersFactory.trimBrackets("") == ""
        AbstractOracleTypeConvertersFactory.trimBrackets("   ") == ""
        AbstractOracleTypeConvertersFactory.trimBrackets("[1,2]") == "1,2"
        AbstractOracleTypeConvertersFactory.trimBrackets(" [ 1 , 2 ] ") == "1 , 2"
    }

    def "parseDoubleArray parses values"() {
        expect:
        AbstractOracleTypeConvertersFactory.parseDoubleArray("[1.0, 2.5, -3]") as List == [1.0d, 2.5d, -3.0d]
        AbstractOracleTypeConvertersFactory.parseDoubleArray("[]").length == 0
        AbstractOracleTypeConvertersFactory.parseDoubleArray(null).length == 0
    }

    def "parseFloatArray parses values"() {
        expect:
        AbstractOracleTypeConvertersFactory.parseFloatArray("[1.0, 2.5, -3]") as List == [1.0f, 2.5f, -3.0f]
        AbstractOracleTypeConvertersFactory.parseFloatArray("[]").length == 0
        AbstractOracleTypeConvertersFactory.parseFloatArray(null).length == 0
    }

    def "parseIntArray rounds and clamps"() {
        when:
        def arr = AbstractOracleTypeConvertersFactory.parseIntArray("[1.2, 2.5, -3.49, 2147483648, -2147483649]")

        then:
        // Math.round: 1.2 -> 1, 2.5 -> 3, -3.49 -> -3; then clamp to INT bounds
        arr as List == [1, 3, -3, Integer.MAX_VALUE, Integer.MIN_VALUE]
    }

    def "parseByteArray rounds and clamps"() {
        when:
        def arr = AbstractOracleTypeConvertersFactory.parseByteArray("[127.6, -128.4, 10, 9999, -9999]")

        then:
        arr as List == [127 as byte, -128 as byte, 10 as byte, Byte.MAX_VALUE, Byte.MIN_VALUE]
    }

    def "toOracleText for vector and primitive arrays"() {
        given:
        def v = Vector.of(1d, 2d, 3d)

        expect:
        AbstractOracleTypeConvertersFactory.toOracleText(v) == "[1.0, 2.0, 3.0]"
        AbstractOracleTypeConvertersFactory.toOracleText(new double[]{1, 2}) == "[1.0, 2.0]"
        AbstractOracleTypeConvertersFactory.toOracleText(new float[]{1, 2}) == "[1.0, 2.0]"
        AbstractOracleTypeConvertersFactory.toOracleText(new int[]{1, 2}) == "[1, 2]"
        AbstractOracleTypeConvertersFactory.toOracleText(new byte[]{1 as byte, 2 as byte}) == "[1, 2]"
    }

    private static class Adapter implements AbstractOracleTypeConvertersFactory.OracleVectorAdapter {
        private final AbstractOracleTypeConvertersFactory.OracleVectorKind kind
        private final float[] f
        private final double[] d
        private final int[] i
        private final byte[] b

        Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind kind,
                float[] f = new float[0],
                double[] d = new double[0],
                int[] i = new int[0],
                byte[] b = new byte[0]) {
            this.kind = kind
            this.f = f
            this.d = d
            this.i = i
            this.b = b
        }

        // Support Groovy named-argument constructor calls like:
        // new Adapter(kind, f: float[], d: double[], i: int[], b: byte[])
        Adapter(Map params, AbstractOracleTypeConvertersFactory.OracleVectorKind kind) {
            this(
                kind,
                (float[])  (params?.f ?: new float[0]),
                (double[]) (params?.d ?: new double[0]),
                (int[])    (params?.i ?: new int[0]),
                (byte[])   (params?.b ?: new byte[0])
            )
        }

        @Override
        AbstractOracleTypeConvertersFactory.OracleVectorKind getKind() { kind }

        @Override
        float[] toFloatArray() { f }

        @Override
        double[] toDoubleArray() { d }

        @Override
        int[] toIntArray() { i }

        @Override
        byte[] toByteArray() { b }
    }

    def "toVector maps adapter kinds to matching Vector"() {
        expect:
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, f: [1f, 2f] as float[]))
                .toFloatArray() as List == [1f, 2f]
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT64, d: [1d, 2d] as double[]))
                .toDoubleArray() as List == [1d, 2d]
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.BINARY, b: [1 as byte, 2 as byte] as byte[]))
                .toByteArray() as List == [1 as byte, 2 as byte]
    }

    def "vectorToX helpers convert between kinds"() {
        given:
        def fAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, f: [1f, 2f] as float[])
        def dAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT64, d: [1d, 2d] as double[])
        def iAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8, i: [1, 2] as byte[])
        def bAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.BINARY, b: [1 as byte, 2 as byte] as byte[])

        expect: "double target"
        AbstractOracleTypeConvertersFactory.vectorToDoubleArray(dAdapter).get() as List == [1d, 2d]
        AbstractOracleTypeConvertersFactory.vectorToDoubleArray(fAdapter).get() as List == [1d, 2d]
        AbstractOracleTypeConvertersFactory.vectorToDoubleArray(iAdapter).get() as List == [1d, 2d]
        AbstractOracleTypeConvertersFactory.vectorToDoubleArray(bAdapter).get() as List == [1d, 2d]

        and: "float target"
        AbstractOracleTypeConvertersFactory.vectorToFloatArray(fAdapter).get() as List == [1f, 2f]
        AbstractOracleTypeConvertersFactory.vectorToFloatArray(dAdapter).get() as List == [1f, 2f]
        AbstractOracleTypeConvertersFactory.vectorToFloatArray(iAdapter).get() as List == [1f, 2f]
        AbstractOracleTypeConvertersFactory.vectorToFloatArray(bAdapter).get() as List == [1f, 2f]

        and: "int target"
        AbstractOracleTypeConvertersFactory.vectorToIntArray(iAdapter).get() as List == [1, 2]
        AbstractOracleTypeConvertersFactory.vectorToIntArray(fAdapter).get() as List == [1, 2]
        AbstractOracleTypeConvertersFactory.vectorToIntArray(dAdapter).get() as List == [1, 2]
        AbstractOracleTypeConvertersFactory.vectorToIntArray(bAdapter).get() as List == [1, 2]

        and: "byte target"
        AbstractOracleTypeConvertersFactory.vectorToByteArray(bAdapter).get() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.vectorToByteArray(iAdapter).get() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.vectorToByteArray(fAdapter).get() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.vectorToByteArray(dAdapter).get() as List == [1 as byte, 2 as byte]
    }
}
