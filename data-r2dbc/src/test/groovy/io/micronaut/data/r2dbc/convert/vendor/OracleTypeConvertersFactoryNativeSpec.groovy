package io.micronaut.data.r2dbc.convert.vendor

import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.ByteVector
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
}
