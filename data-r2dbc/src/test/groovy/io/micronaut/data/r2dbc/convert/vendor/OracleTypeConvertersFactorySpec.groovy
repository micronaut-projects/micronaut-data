package io.micronaut.data.r2dbc.convert.vendor

import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class OracleTypeConvertersFactorySpec extends Specification {

    def "string write converters produce Oracle textual format"() {
        given:
        def factory = new OracleTypeConvertersFactory()

        expect:
        factory.fromVectorToString().convert(Vector.of(1d, 2d), String, null).get() == "[1.0, 2.0]"
        factory.fromDoubleVectorToString().convert((DoubleVector) Vector.of(1d, 2d), String, null).get() == "[1.0, 2.0]"
        factory.fromFloatVectorToString().convert((FloatVector) Vector.of([1f, 2f] as float[]), String, null).get() == "[1.0, 2.0]"
        factory.fromByteVectorToString().convert((ByteVector) Vector.of([1 as byte, 2 as byte] as byte[]), String, null).get() == "[1, 2]"
    }

    def "string read converters parse Oracle textual format"() {
        given:
        def factory = new OracleTypeConvertersFactory()

        expect:
        factory.fromStringToVector().convert("[1.0, 2.0]", Vector, null).get().toDoubleArray().toList() == [1d, 2d]
        factory.fromStringToDoubleVector().convert("[1.0, 2.0]", DoubleVector, null).get().toDoubleArray().toList() == [1d, 2d]
        factory.fromStringToFloatVector().convert("[1.0, 2.0]", FloatVector, null).get().toFloatArray().toList() == [1f, 2f]
        factory.fromStringToByteVector().convert("[1.4, 2.6]", ByteVector, null).get().toByteArray().toList() == [1 as byte, 3 as byte]
    }
}
