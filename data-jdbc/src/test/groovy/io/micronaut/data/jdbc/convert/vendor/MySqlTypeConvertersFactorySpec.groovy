package io.micronaut.data.jdbc.convert.vendor

import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !io.micronaut.data.jdbc.convert.vendor.MySqlTypeConvertersFactorySpec.hasMySql() })
class MySqlTypeConvertersFactorySpec extends Specification {

    static boolean hasMySql() {
        try {
            Class.forName('com.mysql.cj.jdbc.Driver')
            return true
        } catch (Throwable t) {
            return false
        }
    }

    def "Vector values convert to binary and back for supported vector types (JDBC MySQL)"() {
        given:
        def f = new MySqlTypeConvertersFactory()

        when: "generic vector backed by FloatVector -> bytes -> Vector (FloatVector)"
        def gv = Vector.of([1.25f, 2.5f] as float[])
        def gvBin = f.vectorToBinary().convert(gv, byte[], null).get()
        def gvBack = f.binaryToVector().convert(gvBin, Vector, null).get()

        then:
        gvBack.toFloatArray().size() == 2
        Math.abs(gvBack.toFloatArray()[0] - 1.25f) < 1e-6
        Math.abs(gvBack.toFloatArray()[1] - 2.5f) < 1e-6

        when: "float vector -> bytes -> FloatVector"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvBin = f.floatVectorToBinary().convert(fv, byte[], null).get()
        def fvBack = f.binaryToFloatVector().convert(fvBin, FloatVector, null).get()

        then:
        fvBack.toFloatArray().toList() == [1f, 2f]

        when: "generic vector backed by DoubleVector is rejected"
        f.vectorToBinary().convert(Vector.of(1.0d, 2.6d), byte[], null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("MYSQL does not support")

    }

    def "Vector binary conversion rejects payloads with trailing bytes"() {
        given:
        def f = new MySqlTypeConvertersFactory()
        byte[] malformed = [0, 0, 0, 0, 1] as byte[]

        when:
        f.binaryToVector().convert(malformed, Vector, null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid MySQL VECTOR binary length 5")
        ex.message.contains("multiple of 4")
    }
}
