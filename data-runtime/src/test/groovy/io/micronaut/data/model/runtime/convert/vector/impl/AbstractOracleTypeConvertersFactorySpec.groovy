package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.SparseByteVector
import io.micronaut.data.model.vector.SparseDoubleVector
import io.micronaut.data.model.vector.SparseFloatVector
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
        AbstractOracleTypeConvertersFactory.toVector(
            new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, f: [1f, 2f] as float[])
        )
                .toFloatArray() as List == [1f, 2f]
        AbstractOracleTypeConvertersFactory.toVector(
            new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT64, d: [1d, 2d] as double[])
        )
                .toDoubleArray() as List == [1d, 2d]
        AbstractOracleTypeConvertersFactory.toVector(
            new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8, b: [1 as byte, 2 as byte] as byte[])
        )
                .toByteArray() as List == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.toVector(
            new Adapter(
                AbstractOracleTypeConvertersFactory.OracleVectorKind.BINARY,
                b: [1 as byte, 2 as byte] as byte[]
            )
        )
                .toByteArray() as List == [1 as byte, 2 as byte]
    }

    def "vectorToX helpers convert between kinds"() {
        given:
        def fAdapter = new Adapter(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, f: [1f, 2f] as float[])
        def dAdapter = new Adapter(
            AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT64,
            d: [1d, 2d] as double[]
        )
        def iAdapter = new Adapter(
            AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8,
            b: [1 as byte, 2 as byte] as byte[]
        )
        def bAdapter = new Adapter(
            AbstractOracleTypeConvertersFactory.OracleVectorKind.BINARY,
            b: [1 as byte, 2 as byte] as byte[]
        )

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

    def "vectorToOracleVector delegates to matching native factory method"() {
        given:
        def factory = new AbstractOracleTypeConvertersFactory.OracleVectorFactory<String>(
            values -> "float32:${Arrays.toString(values)}",
            values -> "float64:${Arrays.toString(values)}",
            values -> "int8:${Arrays.toString(values)}",
            (length, indices, values) ->
                "sparse-float32:${length}:${Arrays.toString(indices)}:${Arrays.toString(values)}",
            (length, indices, values) -> "sparse-int8:${length}:${Arrays.toString(indices)}:${Arrays.toString(values)}",
            (length, indices, values) ->
                "sparse-float64:${length}:${Arrays.toString(indices)}:${Arrays.toString(values)}"
        )

        expect:
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(
            Vector.of([1f, 2f] as float[]), factory
        ).get() == "float32:[1.0, 2.0]"
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(
            Vector.of([1d, 2d] as double[]), factory
        ).get() == "float64:[1.0, 2.0]"
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(
            Vector.of([1 as byte, 2 as byte] as byte[]), factory
        ).get() == "int8:[1, 2]"
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(
            Vector.of([0f, 3f, 0f] as float[]).toSparseFloatVector(), factory
        ).get() == "sparse-float32:3:[1]:[3.0]"
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(
            Vector.of([0d, 3d, 0d] as double[]).toSparseDoubleVector(), factory
        ).get() == "sparse-float64:3:[1]:[3.0]"
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(
            Vector.of([0 as byte, 3 as byte, 0 as byte] as byte[]).toSparseByteVector(), factory
        ).get() == "sparse-int8:3:[1]:[3]"
    }

    def "oracleVector converter helpers map through adapter factory"() {
        given:
        def adapterFactory = value -> new Adapter(value, f: [1f, 2f] as float[])
        def byteAdapterFactory = value -> new Adapter(value, b: [1 as byte, 2 as byte] as byte[])

        expect:
        AbstractOracleTypeConvertersFactory.oracleVectorToVectorConverter(adapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, Vector, null)
                .get()
                .toFloatArray()
                .toList() == [1f, 2f]
        AbstractOracleTypeConvertersFactory.oracleVectorToDoubleVectorConverter(adapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, DoubleVector, null)
                .get()
                .toDoubleArray()
                .toList() == [1d, 2d]
        AbstractOracleTypeConvertersFactory.oracleVectorToFloatVectorConverter(adapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, FloatVector, null)
                .get()
                .toFloatArray()
                .toList() == [1f, 2f]
        AbstractOracleTypeConvertersFactory.oracleVectorToByteVectorConverter(byteAdapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8, ByteVector, null)
                .get()
                .toByteArray()
                .toList() == [1 as byte, 2 as byte]
        AbstractOracleTypeConvertersFactory.oracleVectorToSparseDoubleVectorConverter(adapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, SparseDoubleVector, null)
                .get()
                .toDoubleArray()
                .toList() == [1d, 2d]
        AbstractOracleTypeConvertersFactory.oracleVectorToSparseFloatVectorConverter(adapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.FLOAT32, SparseFloatVector, null)
                .get()
                .toFloatArray()
                .toList() == [1f, 2f]
        AbstractOracleTypeConvertersFactory.oracleVectorToSparseByteVectorConverter(byteAdapterFactory)
                .convert(AbstractOracleTypeConvertersFactory.OracleVectorKind.INT8, SparseByteVector, null)
                .get()
                .toByteArray()
                .toList() == [1 as byte, 2 as byte]
    }

    def "vectorToOracleVector wraps native factory failure"() {
        given:
        def failure = new IllegalArgumentException("bad vector")
        def factory = new AbstractOracleTypeConvertersFactory.OracleVectorFactory<String>(
            { throw failure },
            { throw failure },
            { throw failure },
            { length, indices, values -> throw failure },
            { length, indices, values -> throw failure },
            { length, indices, values -> throw failure }
        )

        when:
        AbstractOracleTypeConvertersFactory.vectorToOracleVector(Vector.of([1f, 2f] as float[]), factory)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Cannot convert Vector to oracle.sql.VECTOR")
        !e.message.contains("[1.0, 2.0]")
        e.cause.is(failure)
    }

}
