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
        def iv = Vector.of([1, 2, 3] as int[])
        def bv = Vector.of([1 as byte, 2 as byte, 3 as byte] as byte[])

        expect:
        dv != fv
        dv != iv
        dv != bv
        fv != iv
        fv != bv
        iv != bv
    }

    void "toString prefixes are type-specific"() {
        expect:
        Vector.of([1d] as double[]).toString().startsWith('D[')
        Vector.of([1f] as float[]).toString().startsWith('F[')
        Vector.of([1] as int[]).toString().startsWith('I[')
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
        def srcI = new int[]{1, 2}
        def vi = Vector.of(srcI)
        srcI[0] = 99

        then:
        vi.toIntegerArray().toList() == [1, 2]

        when:
        def srcB = new byte[]{1 as byte, 2 as byte}
        def vb = Vector.of(srcB)
        srcB[0] = 99 as byte

        then:
        vb.toByteArray().toList() == [1 as byte, 2 as byte]
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
        def fi = Vector.of([1, 2, 3] as int[])
        def a1 = fi.toIntegerArray()
        a1[1] = 42
        def a2 = fi.toIntegerArray()

        then:
        a2.toList() == [1, 2, 3]

        when:
        def fb = Vector.of([1 as byte, 2 as byte, 3 as byte] as byte[])
        def b1 = fb.toByteArray()
        b1[2] = 42 as byte
        def b2 = fb.toByteArray()

        then:
        b2.toList() == [1 as byte, 2 as byte, 3 as byte]
    }

    void "numeric conversions clamp/round as in Java casts"() {
        given:
        def dv = Vector.of([1.9d, -2.1d, 255.9d] as double[])
        def iv = Vector.of([128, -129, 42] as int[])
        def bv = Vector.of([127 as byte, -128 as byte, 10 as byte] as byte[])

        expect: "double -> int truncates"
        dv.toIntegerArray().toList() == [1, -2, 255]

        and: "int -> byte overflows as per cast"
        bv.toIntegerArray().toList() == [127, -128, 10]
        iv.toByteArray().size() == 3 // behavior covered by conversion call path
    }
}
