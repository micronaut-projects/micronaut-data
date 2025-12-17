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

        when: "generic vector (double input) -> bytes -> Vector (FloatVector)"
        def dv = Vector.of(1.25d, 2.5d)
        def dvBin = f.vectorToBinary().convert(dv, byte[], null).get()
        def dvBack = f.binaryToVector().convert(dvBin, Vector, null).get()

        then:
        dvBack.toFloatArray().size() == 2
        Math.abs(dvBack.toFloatArray()[0] - 1.25f) < 1e-6
        Math.abs(dvBack.toFloatArray()[1] - 2.5f) < 1e-6

        when: "float vector -> bytes -> FloatVector"
        def fv = (FloatVector) Vector.of([1f, 2f] as float[])
        def fvBin = f.floatVectorToBinary().convert(fv, byte[], null).get()
        def fvBack = f.binaryToFloatVector().convert(fvBin, FloatVector, null).get()

        then:
        fvBack.toFloatArray().toList() == [1f, 2f]

        when: "generic vector -> bytes -> Vector (FloatVector)"
        def gen = Vector.of(1.0d, 2.6d)
        def genBin = f.vectorToBinary().convert(gen, byte[], null).get()
        def vGenBack = f.binaryToVector().convert(genBin, Vector, null).get()

        then:
        vGenBack != null
        vGenBack.toFloatArray().toList() == [1f, 2.6f]
    }
}
