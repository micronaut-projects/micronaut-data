package io.micronaut.data.jdbc.convert.vendor

import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !io.micronaut.data.jdbc.convert.vendor.PostgresTypeConvertersFactorySpec.hasPg() })
class PostgresTypeConvertersFactorySpec extends Specification {

    static boolean hasPg() {
        try {
            Class.forName('org.postgresql.util.PGobject')
            return true
        } catch (Throwable t) {
            return false
        }
    }

    def "Vector and primitive arrays are converted to PGobject with type vector"() {
        given:
        def f = new PostgresTypeConvertersFactory()


        when: "float vector -> PGobject (cast to double textual form)"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvPg = f.fromFloatVectorToPgObject().convert(fv, Object, null).get()

        then:
        fvPg.getType().equalsIgnoreCase("vector")
        fvPg.getValue() == "[1.0, 2.0]"



        when: "float[] -> PGobject"
        def faPg = f.fromFloatArrayToPgObject().convert([1f, 2f] as float[], Object, null).get()

        then:
        faPg.getType().equalsIgnoreCase("vector")
        faPg.getValue() == "[1.0, 2.0]"


    }

    def "PGobject read path converts back to Vector subtypes (vector and halfvec types only)"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        and: "pgobject with type vector"
        def pgClass = Class.forName('org.postgresql.util.PGobject')
        def pg = pgClass.getDeclaredConstructor().newInstance()
        pg.setType("vector")
        pg.setValue("[1.0, 2.6]")

        // no dedicated DoubleVector mapping; vectors are represented as float-based

        and: "float"
        f.fromPgObjectToFloatVector().convert(pg, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and: "vector"
        f.fromPgObjectToVector().convert(pg, Vector, null).get().toFloatArray().toList() == [1f, 2.6f]

        when: "type halfvec should also be accepted"
        def half = pgClass.getDeclaredConstructor().newInstance()
        half.setType("halfvec")
        half.setValue("[3.1, 4.9]")
        def halfF = f.fromPgObjectToFloatVector().convert(half, FloatVector, null)

        then:
        halfF.isPresent()
        halfF.get().toFloatArray().toList() == [3.1f, 4.9f]

        when: "wrong type should be ignored"
        def wrong = pgClass.getDeclaredConstructor().newInstance()
        wrong.setType("jsonb")
        wrong.setValue("[1,2]")

        then:
        !f.fromPgObjectToFloatVector().convert(wrong, FloatVector, null).isPresent()
        !f.fromPgObjectToVector().convert(wrong, Vector, null).isPresent()
    }
}
