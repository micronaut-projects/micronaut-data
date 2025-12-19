package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

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
        AbstractOracleTypeConvertersFactory.toOracleText(new byte[]{1 as byte, 2 as byte}) == "[1, 2]"
    }

    private static class Adapter implements AbstractOracleTypeConvertersFactory.OracleVectorAdapter {
        private final AbstractOracleTypeConvertersFactory.OracleVectorKind kind
        private final float[] f
        private final double[] d
        private final byte[] b

        Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind kind,
                float[] f = new float[0],
                double[] d = new double[0],
                byte[] b = new byte[0]) {
            this.kind = kind
            this.f = f
            this.d = d
            this.b = b
        }

        // Support Groovy named-argument constructor calls like:
        // new Adapter(kind, f: float[], d: double[], b: byte[])
        Adapter(Map params, AbstractOracleTypeConvertersFactory.OracleVectorKind kind) {
            this(
                kind,
                (float[])  (params?.f ?: new float[0]),
                (double[]) (params?.d ?: new double[0]),
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
        byte[] toByteArray() { b }
    }

    def "toVector maps adapter kinds to matching Vector"() {
        expect:
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, f: [1f, 2f] as float[]))
                .toFloatArray() as List == [1f, 2f]
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT64, d: [1d, 2d] as double[]))
                .toDoubleArray() as List == [1d, 2d]
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8, b: [1 as byte, 2 as byte] as byte[]))
                .toByteArray() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.toVector(new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.BINARY, b: [1 as byte, 2 as byte] as byte[]))
                .toByteArray() as List == [1 as byte, 2 as byte]
    }

    def "vectorToX helpers convert between kinds"() {
        given:
        def fAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, f: [1f, 2f] as float[])
        def dAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT64, d: [1d, 2d] as double[])
        def iAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8, b: [1 as byte, 2 as byte] as byte[])
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

        and: "byte target"
        AbstractOracleTypeConvertersFactory.vectorToByteArray(bAdapter).get() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.vectorToByteArray(iAdapter).get() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.vectorToByteArray(fAdapter).get() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.vectorToByteArray(dAdapter).get() as List == [1 as byte, 2 as byte]
    }
}
