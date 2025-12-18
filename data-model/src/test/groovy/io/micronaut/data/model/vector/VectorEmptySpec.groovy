package io.micronaut.data.model.vector

import spock.lang.Specification

class VectorEmptySpec extends Specification {

    void "of(empty double[]) yields empty DoubleVector and conversions are empty"() {
        given:
        double[] arr = new double[0]

        when:
        Vector v = Vector.of(arr)

        then:
        v instanceof DoubleVector
        v.type == Double.TYPE
        v.toDoubleArray().length == 0
        v.toFloatArray().length == 0
        v.toByteArray().length == 0
    }

    void "of(empty float[]) yields empty FloatVector and conversions are empty"() {
        given:
        float[] arr = new float[0]

        when:
        Vector v = Vector.of(arr)

        then:
        v instanceof FloatVector
        v.type == Float.TYPE
        v.toFloatArray().length == 0
        v.toDoubleArray().length == 0
        v.toByteArray().length == 0
    }

    void "of(empty byte[]) yields empty ByteVector and conversions are empty"() {
        given:
        byte[] arr = new byte[0]

        when:
        Vector v = Vector.of(arr)

        then:
        v instanceof ByteVector
        v.type == Byte.TYPE
        v.toByteArray().length == 0
        v.toFloatArray().length == 0
        v.toDoubleArray().length == 0
    }

    void "of(empty collection) yields empty DoubleVector by default"() {
        given:
        List<Number> values = []

        when:
        Vector v = Vector.of(values)

        then:
        v instanceof DoubleVector
        v.type == Double.TYPE
        v.toDoubleArray().length == 0
        v.toFloatArray().length == 0
        v.toByteArray().length == 0
    }

    void "collection type inference: all Byte -> ByteVector; all Integer -> DoubleVector; all Float -> FloatVector; mixed -> DoubleVector"() {
        expect:
        Vector.of(([1 as byte, 2 as byte, 3 as byte] as List<Byte>)).class == ByteVector
        Vector.of(([1, 2, 3] as List<Integer>)).class == DoubleVector
        Vector.of(([1f, 2f, 3f] as List<Float>)).class == FloatVector
        Vector.of(([1 as byte, 2, 3f, 4d] as List<Number>)).class == DoubleVector
    }

    void "null arguments to of(...) throw NPE"() {
        when:
        Vector.of((double[]) null)

        then:
        thrown(NullPointerException)

        when:
        Vector.of((float[]) null)

        then:
        thrown(NullPointerException)

        when:
        Vector.of((byte[]) null)

        then:
        thrown(NullPointerException)

        when:
        Vector.of((Collection<? extends Number>) null)

        then:
        thrown(NullPointerException)
    }
}
