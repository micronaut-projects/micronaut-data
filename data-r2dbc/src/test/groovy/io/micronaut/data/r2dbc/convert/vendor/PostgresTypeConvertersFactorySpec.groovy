package io.micronaut.data.r2dbc.convert.vendor

import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

class PostgresTypeConvertersFactorySpec extends Specification {

    def "FloatVector and float[] are converted to r2dbc codec Vector"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        when: "float vector -> codec vector"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvCodec = f.fromFloatVectorToPgObject().convert(fv, io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        fvCodec instanceof io.r2dbc.postgresql.codec.Vector
        fvCodec.getVector() as List == [1f, 2f]

        when: "float[] -> codec vector"
        def faCodec = f.fromFloatArrayToPgObject().convert([1f, 2.6f] as float[], io.r2dbc.postgresql.codec.Vector, null).get()

        then:
        faCodec.getVector() as List == [1f, 2.6f]
    }

    def "sparse float arrays are rejected for Postgres R2DBC codec binding"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def sparse = [1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f] as float[]

        when:
        f.fromFloatArrayToPgObject().convert(sparse, io.r2dbc.postgresql.codec.Vector, null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('Sparse vectors are not supported')
    }

    def "codec Vector is converted back to FloatVector and Vector"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def codec = io.r2dbc.postgresql.codec.Vector.of([1f, 2.6f] as float[])

        expect: "null input yields Optional.empty"
        !f.fromPgObjectToFloatVector().convert(null, FloatVector, null).isPresent()
        !f.fromPgObjectToVector().convert(null, Vector, null).isPresent()

        and: "float"
        f.fromPgObjectToFloatVector().convert(codec, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and: "vector"
        f.fromPgObjectToVector().convert(codec, Vector, null).get().toFloatArray().toList() == [1f, 2.6f]
    }
}
