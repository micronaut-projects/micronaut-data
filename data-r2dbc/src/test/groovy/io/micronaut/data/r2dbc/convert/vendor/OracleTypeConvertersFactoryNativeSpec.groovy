package io.micronaut.data.r2dbc.convert.vendor

import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.SparseByteVector
import io.micronaut.data.model.vector.SparseDoubleVector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.Vector
import oracle.jdbc.OracleType
import oracle.sql.VECTOR
import spock.lang.Specification

class OracleTypeConvertersFactoryNativeSpec extends Specification {

    private final OracleTypeConvertersFactory factory = new OracleTypeConvertersFactory()

    def "does not expose string to oracle vector converter"() {
        expect:
        !OracleTypeConvertersFactory.declaredMethods*.name.contains('fromStringToOracleVector')
    }

    def "converts oracle float64 vector to double vector"() {
        given:
        def oracleVector = VECTOR.ofFloat64Values([0d, 10d, 0d, 20d, 0d] as double[])

        when:
        def converted = factory.fromOracleVectorToDoubleVector().convert(oracleVector, DoubleVector, null).orElse(null)

        then:
        converted != null
        converted.toDoubleArray().toList() == [0d, 10d, 0d, 20d, 0d]
    }

    def "converts oracle float32 vector to float vector"() {
        given:
        def oracleVector = VECTOR.ofFloat32Values([1.5f, 0f, 3.5f, 0f] as float[])

        when:
        def converted = factory.fromOracleVectorToFloatVector().convert(oracleVector, FloatVector, null).orElse(null)

        then:
        converted != null
        converted.toFloatArray().toList() == [1.5f, 0f, 3.5f, 0f]
    }

    def "converts oracle int8 vector to byte vector"() {
        given:
        def oracleVector = VECTOR.ofInt8Values([0 as byte, 10 as byte, 0 as byte, 20 as byte] as byte[])

        when:
        def converted = factory.fromOracleVectorToByteVector().convert(oracleVector, ByteVector, null).orElse(null)

        then:
        converted != null
        converted.toByteArray().toList() == [0 as byte, 10 as byte, 0 as byte, 20 as byte]
    }

    def "converts sparse oracle float32 vector to dense float vector"() {
        given:
        def oracleVector = VECTOR.ofFloat32Values(VECTOR.SparseFloatArray.of(5, [1, 4] as int[], [2f, 9f] as float[]))

        when:
        def converted = factory.fromOracleVectorToFloatVector().convert(oracleVector, FloatVector, null).orElse(null)

        then:
        converted != null
        converted.toFloatArray().toList() == [0f, 2f, 0f, 0f, 9f]
    }

    def "converts sparse oracle int8 vector to dense byte vector"() {
        given:
        def oracleVector = VECTOR.ofInt8Values(VECTOR.SparseByteArray.of(4, [0, 3] as int[], [7 as byte, 1 as byte] as byte[]))

        when:
        def converted = factory.fromOracleVectorToByteVector().convert(oracleVector, ByteVector, null).orElse(null)

        then:
        converted != null
        converted.toByteArray().toList() == [7 as byte, 0 as byte, 0 as byte, 1 as byte]
    }

    def "converts sparse float and byte vectors to typed oracle vectors"() {
        when:
        def floatOracle = factory.fromVectorToOracleVector().convert(new SparseFloatVector(4, [1, 3] as int[], [1.5f, 2.5f] as float[]), VECTOR, null).orElse(null)
        def byteOracle = factory.fromVectorToOracleVector().convert(new SparseByteVector(4, [0, 2] as int[], [3 as byte, 5 as byte] as byte[]), VECTOR, null).orElse(null)
        def doubleOracle = factory.fromVectorToOracleVector().convert(new SparseDoubleVector(4, [1, 2] as int[], [4.5d, 6.75d] as double[]), VECTOR, null).orElse(null)

        then:
        floatOracle != null
        floatOracle.type == OracleType.VECTOR_FLOAT32
        byteOracle != null
        byteOracle.type == OracleType.VECTOR_INT8
        doubleOracle != null
        doubleOracle.type == OracleType.VECTOR_FLOAT64
    }

    def "converts oracle vectors to sparse vector targets"() {
        when:
        def sparseDouble = factory.fromOracleVectorToSparseDoubleVector().convert(
            VECTOR.ofFloat64Values([0d, 3d, 0d, 4d] as double[]),
            SparseDoubleVector,
            null
        ).orElse(null)
        def sparseFloat = factory.fromOracleVectorToSparseFloatVector().convert(
            VECTOR.ofFloat32Values([0f, 2f, 0f, 5f] as float[]),
            SparseFloatVector,
            null
        ).orElse(null)
        def sparseByte = factory.fromOracleVectorToSparseByteVector().convert(
            VECTOR.ofInt8Values([0 as byte, 7 as byte, 0 as byte, 1 as byte] as byte[]),
            SparseByteVector,
            null
        ).orElse(null)

        then:
        sparseDouble != null
        sparseDouble.indices().toList() == [1, 3]
        sparseDouble.values().toList() == [3d, 4d]
        sparseFloat != null
        sparseFloat.indices().toList() == [1, 3]
        sparseFloat.values().toList() == [2f, 5f]
        sparseByte != null
        sparseByte.indices().toList() == [1, 3]
        sparseByte.values().toList() == [7 as byte, 1 as byte]
    }

    def "converts dense vectors to expected oracle vector types"() {
        when:
        def floatOracle = factory.fromVectorToOracleVector().convert((FloatVector) Vector.of([1f, 2f] as float[]), VECTOR, null).orElse(null)
        def byteOracle = factory.fromVectorToOracleVector().convert((ByteVector) Vector.of([1 as byte, 2 as byte] as byte[]), VECTOR, null).orElse(null)
        def doubleOracle = factory.fromVectorToOracleVector().convert((DoubleVector) Vector.of([1d, 2d] as double[]), VECTOR, null).orElse(null)

        then:
        floatOracle.type == OracleType.VECTOR_FLOAT32
        byteOracle.type == OracleType.VECTOR_INT8
        doubleOracle.type == OracleType.VECTOR_FLOAT64
    }
}
