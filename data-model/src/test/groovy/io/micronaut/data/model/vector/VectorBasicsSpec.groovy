package io.micronaut.data.model.vector

import spock.lang.Specification

class VectorBasicsSpec extends Specification {

    void "equals and hashCode for same type and values"() {
        given:
        def a = new double[]{1d, 2d, 3d}
        def b = new double[]{1d, 2d, 3d}
        def v1 = Vector.of(a)
        def v2 = Vector.of(b)

        expect:
        v1 instanceof DoubleVector
        v2 instanceof DoubleVector
        v1 == v2
        v1.hashCode() == v2.hashCode()
    }

    void "different backing types are not equal even if numerically equal"() {
        given:
        def dv = Vector.of([1d, 2d, 3d] as double[])
        def fv = Vector.of([1f, 2f, 3f] as float[])
        def bv = Vector.of([1 as byte, 2 as byte, 3 as byte] as byte[])

        expect:
        dv != fv
        dv != bv
        fv != bv
    }

    void "toString prefixes are type-specific"() {
        expect:
        Vector.of([1d] as double[]).toString().startsWith('D[')
        Vector.of([1f] as float[]).toString().startsWith('F[')
        Vector.of([1 as byte] as byte[]).toString().startsWith('B[')
    }

    void "Vector.of performs defensive copy of input arrays"() {
        when: "construct from array, then mutate the source"
        def srcD = new double[]{1d, 2d}
        def vd = Vector.of(srcD)
        srcD[0] = 99d

        then: "vector content is unchanged"
        vd.toDoubleArray().toList() == [1d, 2d]

        when:
        def srcF = new float[]{1f, 2f}
        def vf = Vector.of(srcF)
        srcF[0] = 99f

        then:
        vf.toFloatArray().toList() == [1f, 2f]

        when:
        def srcB = new byte[]{1 as byte, 2 as byte}
        def vb = Vector.of(srcB)
        srcB[0] = 99 as byte

        then:
        vb.toByteArray().toList() == [1 as byte, 2 as byte]
    }

    void "record component data accessor returns a defensive copy"() {
        when:
        def doubleVector = Vector.of([1d, 2d] as double[]) as DoubleVector
        def doubleData = doubleVector.data()
        doubleData[0] = 99d

        then:
        doubleVector.toDoubleArray().toList() == [1d, 2d]

        when:
        def floatVector = Vector.of([1f, 2f] as float[]) as FloatVector
        def floatData = floatVector.data()
        floatData[0] = 99f

        then:
        floatVector.toFloatArray().toList() == [1f, 2f]

        when:
        def byteVector = Vector.of([1 as byte, 2 as byte] as byte[]) as ByteVector
        def byteData = byteVector.data()
        byteData[0] = 99 as byte

        then:
        byteVector.toByteArray().toList() == [1 as byte, 2 as byte]
    }

    void "toXxxArray returns a fresh copy each call"() {
        given:
        def v = Vector.of([1d, 2d, 3d] as double[])

        when:
        def arr1 = v.toDoubleArray()
        arr1[0] = 42d
        def arr2 = v.toDoubleArray()

        then:
        arr2.toList() == [1d, 2d, 3d]


        when:
        def fb = Vector.of([1 as byte, 2 as byte, 3 as byte] as byte[])
        def b1 = fb.toByteArray()
        b1[2] = 42 as byte
        def b2 = fb.toByteArray()

        then:
        b2.toList() == [1 as byte, 2 as byte, 3 as byte]
    }
}
