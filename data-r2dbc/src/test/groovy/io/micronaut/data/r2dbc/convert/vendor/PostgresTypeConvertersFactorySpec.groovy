package io.micronaut.data.r2dbc.convert.vendor

import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.IntVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class PostgresTypeConvertersFactorySpec extends Specification {

    def "Vector and primitive arrays are converted to r2dbc codec Vector"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        when: "double vector -> codec vector (downcast to float)"
        def dv = (DoubleVector) Vector.of(1.25d, 2.5d)
        def dvCodec = f.fromDoubleVectorToPgObject().convert(dv, io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        dvCodec instanceof io.r2dbc.postgresql.codec.Vector
        dvCodec.getVector() as List == [1.25f, 2.5f]

        when: "float vector -> codec vector"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvCodec = f.fromFloatVectorToPgObject().convert(fv, io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        fvCodec.getVector() as List == [1f, 2f]

        when: "int vector -> codec vector"
        def iv = (IntVector) Vector.of([1, 2] as int[])
        def ivCodec = f.fromIntVectorToPgObject().convert(iv, io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        ivCodec.getVector() as List == [1f, 2f]

        when: "byte vector -> codec vector"
        def bv = (ByteVector) Vector.of([1 as byte, 2 as byte] as byte[])
        def bvCodec = f.fromByteVectorToPgObject().convert(bv, io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        bvCodec.getVector() as List == [1f, 2f]

        when: "double[] -> codec vector"
        def daCodec = f.fromDoubleArrayToPgObject().convert([1.0d, 2.0d] as double[], io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        daCodec.getVector() as List == [1f, 2f]

        when: "float[] -> codec vector"
        def faCodec = f.fromFloatArrayToPgObject().convert([1f, 2f] as float[], io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        faCodec.getVector() as List == [1f, 2f]

        when: "int[] -> codec vector"
        def iaCodec = f.fromIntArrayToPgObject().convert([1, 2] as int[], io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        iaCodec.getVector() as List == [1f, 2f]

        when: "byte[] -> codec vector"
        def baCodec = f.fromByteArrayToPgObject().convert([1 as byte, 2 as byte] as byte[], io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        baCodec.getVector() as List == [1f, 2f]
    }

    def "codec Vector is converted back to Vector subtypes and Vector"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def codec = io.r2dbc.postgresql.codec.Vector.of([1f, 2.6f] as float[])

        expect: "null input yields Optional.empty"
        !f.fromPgObjectToDoubleVector().convert(null, DoubleVector, null).isPresent()
        !f.fromPgObjectToFloatVector().convert(null, FloatVector, null).isPresent()
        !f.fromPgObjectToVector().convert(null, Vector, null).isPresent()
        !f.fromPgObjectToIntVector().convert(null, IntVector, null).isPresent()

        and: "double"
        f.fromPgObjectToDoubleVector().convert(codec, DoubleVector, null).get().toDoubleArray().toList() == [1d, 2.6d]

        and: "float"
        f.fromPgObjectToFloatVector().convert(codec, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and: "vector"
        f.fromPgObjectToVector().convert(codec, Vector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and: "int (rounded)"
        f.fromPgObjectToIntVector().convert(codec, IntVector, null).get().toIntegerArray().toList() == [1, 3]
    }

    def "cross-type adapters produce IntVector"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        expect:
        f.fromDoubleVectorToIntVector().convert((DoubleVector) Vector.of(1.4d, 2.6d), IntVector, null).get().toIntegerArray().toList() == [1, 3]
        f.fromFloatVectorToIntVector().convert((FloatVector) Vector.of([1.4f, 2.6f] as float[]), IntVector, null).get().toIntegerArray().toList() == [1, 3]
        f.fromByteVectorToIntVector().convert((ByteVector) Vector.of([1 as byte, 2 as byte] as byte[]), IntVector, null).get().toIntegerArray().toList() == [1, 2]
    }
}
