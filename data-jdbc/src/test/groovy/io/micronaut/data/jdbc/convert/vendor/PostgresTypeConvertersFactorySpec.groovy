package io.micronaut.data.jdbc.convert.vendor

import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !io.micronaut.data.jdbc.convert.vendor.PostgresTypeConvertersFactorySpec.hasPg() })
class PostgresTypeConvertersFactorySpec extends Specification {

    static boolean hasPg() {
        try {
            Class.forName('com.pgvector.PGvector')
            return true
        } catch (Throwable t) {
            return false
        }
    }

    def "Vector and primitive arrays are converted to PGvector"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        when: "float vector -> PGvector"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvPg = f.fromFloatVectorToPgObject().convert(fv, Object, null).get()

        then:
        fvPg.getClass().name == 'com.pgvector.PGvector'
        fvPg.toArray().toList() == [1f, 2f]

        when: "float[] -> PGvector"
        def faPg = f.fromFloatArrayToPgObject().convert([1f, 2f] as float[], Object, null).get()

        then:
        faPg.getClass().name == 'com.pgvector.PGvector'
        faPg.toArray().toList() == [1f, 2f]
    }

    def "PGvector read path converts back to Vector subtypes"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        and: "PGvector instance"
        def pgClass = Class.forName('com.pgvector.PGvector')
        def ctor = pgClass.getDeclaredConstructor(float[].class)
        def pg = ctor.newInstance([([1f, 2.6f] as float[])] as Object[])

        expect: "float"
        f.fromPgObjectToFloatVector().convert(pg, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and: "vector"
        f.fromPgObjectToVector().convert(pg, Vector, null).get().toFloatArray().toList() == [1f, 2.6f]
    }
}
