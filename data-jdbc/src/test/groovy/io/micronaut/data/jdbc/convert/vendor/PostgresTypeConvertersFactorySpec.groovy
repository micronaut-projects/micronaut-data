package io.micronaut.data.jdbc.convert.vendor

import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.IntVector
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

        when: "double vector -> PGobject (keeps double textual form)"
        def dv = (DoubleVector) Vector.of(1.25d, 2.5d)
        def dvPg = f.fromDoubleVectorToPgObject().convert(dv, Object, null).get()

        then:
        dvPg.getType().equalsIgnoreCase("vector")
        dvPg.getValue() == "[1.25, 2.5]"

        when: "float vector -> PGobject (cast to double textual form)"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvPg = f.fromFloatVectorToPgObject().convert(fv, Object, null).get()

        then:
        fvPg.getType().equalsIgnoreCase("vector")
        fvPg.getValue() == "[1.0, 2.0]"

        when: "int vector -> PGobject (cast to double textual form)"
        def iv = (IntVector) Vector.of([1, 2] as int[])
        def ivPg = f.fromIntVectorToPgObject().convert(iv, Object, null).get()

        then:
        ivPg.getType().equalsIgnoreCase("vector")
        ivPg.getValue() == "[1.0, 2.0]"

        when: "byte vector -> PGobject (cast to double textual form)"
        def bv = (ByteVector) Vector.of([1 as byte, 2 as byte] as byte[])
        def bvPg = f.fromByteVectorToPgObject().convert(bv, Object, null).get()

        then:
        bvPg.getType().equalsIgnoreCase("vector")
        bvPg.getValue() == "[1.0, 2.0]"

        when: "double[] -> PGobject"
        def daPg = f.fromDoubleArrayToPgObject().convert([1.0d, 2.0d] as double[], Object, null).get()

        then:
        daPg.getType().equalsIgnoreCase("vector")
        daPg.getValue() == "[1.0, 2.0]"

        when: "float[] -> PGobject"
        def faPg = f.fromFloatArrayToPgObject().convert([1f, 2f] as float[], Object, null).get()

        then:
        faPg.getType().equalsIgnoreCase("vector")
        faPg.getValue() == "[1.0, 2.0]"

        when: "int[] -> PGobject"
        def iaPg = f.fromIntArrayToPgObject().convert([1, 2] as int[], Object, null).get()

        then:
        iaPg.getType().equalsIgnoreCase("vector")
        iaPg.getValue() == "[1.0, 2.0]"

        when: "byte[] -> PGobject"
        def baPg = f.fromByteArrayToPgObject().convert([1 as byte, 2 as byte] as byte[], Object, null).get()

        then:
        baPg.getType().equalsIgnoreCase("vector")
        baPg.getValue() == "[1.0, 2.0]"
    }

    def "PGobject read path converts back to Vector subtypes (vector and halfvec types only)"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        and: "pgobject with type vector"
        def pgClass = Class.forName('org.postgresql.util.PGobject')
        def pg = pgClass.getDeclaredConstructor().newInstance()
        pg.setType("vector")
        pg.setValue("[1.0, 2.6]")

        expect: "double"
        f.fromPgObjectToDoubleVector().convert(pg, DoubleVector, null).get().toDoubleArray().toList() == [1d, 2.6d]

        and: "float"
        f.fromPgObjectToFloatVector().convert(pg, FloatVector, null).get().toFloatArray().toList() == [1f, 2.6f]

        and: "vector"
        f.fromPgObjectToVector().convert(pg, Vector, null).get().toDoubleArray().toList() == [1d, 2.6d]

        and: "int rounded"
        f.fromPgObjectToIntVector().convert(pg, IntVector, null).get().toIntegerArray().toList() == [1, 3]

        when: "type halfvec should also be accepted"
        def half = pgClass.getDeclaredConstructor().newInstance()
        half.setType("halfvec")
        half.setValue("[3.1, 4.9]")
        def halfD = f.fromPgObjectToDoubleVector().convert(half, DoubleVector, null)

        then:
        halfD.isPresent()
        halfD.get().toDoubleArray().toList() == [3.1d, 4.9d]

        when: "wrong type should be ignored"
        def wrong = pgClass.getDeclaredConstructor().newInstance()
        wrong.setType("jsonb")
        wrong.setValue("[1,2]")

        then:
        !f.fromPgObjectToDoubleVector().convert(wrong, DoubleVector, null).isPresent()
        !f.fromPgObjectToFloatVector().convert(wrong, FloatVector, null).isPresent()
        !f.fromPgObjectToVector().convert(wrong, Vector, null).isPresent()
        !f.fromPgObjectToIntVector().convert(wrong, IntVector, null).isPresent()
    }

    def "cross-type adapters produce IntVector (rounding where applicable)"() {
        given:
        def f = new PostgresTypeConvertersFactory()

        expect:
        f.fromDoubleVectorToIntVector().convert((DoubleVector) Vector.of(1.4d, 2.6d), IntVector, null).get().toIntegerArray().toList() == [1, 3]
        f.fromFloatVectorToIntVector().convert((FloatVector) Vector.of([1.4f, 2.6f] as float[]), IntVector, null).get().toIntegerArray().toList() == [1, 3]
        f.fromByteVectorToIntVector().convert((ByteVector) Vector.of([1 as byte, 2 as byte] as byte[]), IntVector, null).get().toIntegerArray().toList() == [1, 2]
    }
}
