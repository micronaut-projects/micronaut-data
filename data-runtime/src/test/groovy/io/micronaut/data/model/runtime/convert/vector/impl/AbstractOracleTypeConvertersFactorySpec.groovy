package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class AbstractOracleTypeConvertersFactorySpec extends Specification {

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
