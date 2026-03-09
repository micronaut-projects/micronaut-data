package io.micronaut.data.jdbc.convert.vendor

import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import org.postgresql.util.PGobject
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

        when: "float vector -> PGobject (dense vector)"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvPg = f.fromFloatVectorToPgObject().convert(fv, Object, null).get()

        then:
        fvPg.getClass().name == 'org.postgresql.util.PGobject'
        fvPg.type == 'vector'
        fvPg.value == '[1.0,2.0]'

        when: "float[] -> PGobject (dense vector)"
        def faPg = f.fromFloatArrayToPgObject().convert([1f, 2f] as float[], Object, null).get()

        then:
        faPg.getClass().name == 'org.postgresql.util.PGobject'
        faPg.type == 'vector'
        faPg.value == '[1.0,2.0]'
    }

    def "sparse float vectors are converted to sparsevec PGobject"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def sparse = ([0f, 0f, 2.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 3f] as float[])

        when:
        def pg = f.fromFloatArrayToPgObject().convert(sparse, Object, null).get()

        then:
        pg.type == 'sparsevec'
        pg.value == '{3:2.5,16:3.0}/16'
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

        and: "PGobject dense vector"
        def pgObject = new PGobject()
        pgObject.setType('vector')
        pgObject.setValue('[1.0,2.6]')
        f.fromPgObjectToFloatVector().convert(pgObject, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]
    }

    def "PGobject sparsevec read path converts to dense PGvector"() {
        given:
        def f = new PostgresTypeConvertersFactory()
        def sparse = new PGobject()
        sparse.setType("sparsevec")
        sparse.setValue("{1:1,3:2}/4")

        when:
        def pg = f.fromPgObjectToPgVector().convert(sparse, Object, null).get()

        then:
        pg.getClass().name == 'com.pgvector.PGvector'
        pg.toArray().toList() == [1f, 0f, 2f, 0f]
    }
}
