package io.micronaut.data.model.vector

import spock.lang.Specification

class SparseVectorSpec extends Specification {

    void "dense vector converts to sparse and back"() {
        given:
        def dense = Vector.of([0f, 1f, 0f, 2f] as float[])

        when:
        SparseFloatVector sparse = dense.toSparseFloatVector()

        then:
        sparse.length() == 4
        sparse.indices().toList() == [1, 3]
        sparse.values().toList() == [1f, 2f]
        sparse.toFloatArray().toList() == [0f, 1f, 0f, 2f]
    }

    void "sparse vector validates index constraints"() {
        when:
        new SparseDoubleVector(3, [2, 2] as int[], [1d, 2d] as double[])

        then:
        thrown(IllegalArgumentException)
    }

    void "sparse vector validates shape variants"() {
        when:
        new SparseFloatVector(-1, [] as int[], [] as float[])

        then:
        thrown(IllegalArgumentException)

        when:
        new SparseByteVector(3, [0, 2] as int[], [1] as byte[])

        then:
        thrown(IllegalArgumentException)

        when:
        new SparseDoubleVector(2, [2] as int[], [1d] as double[])

        then:
        thrown(IllegalArgumentException)
    }

    void "sparse byte vector converts to dense byte array"() {
        given:
        def sparse = new SparseByteVector(5, [0, 4] as int[], [1, 9] as byte[])

        expect:
        sparse.toByteArray().toList() == [1, 0, 0, 0, 9]
    }

    void "sparse vectors toString include array contents"() {
        expect:
        new SparseByteVector(5, [0, 4] as int[], [1, 9] as byte[]).toString() ==
            "SparseByteVector[length=5, indices=[0, 4], values=[1, 9]]"
        new SparseFloatVector(4, [1, 3] as int[], [1f, 2f] as float[]).toString() ==
            "SparseFloatVector[length=4, indices=[1, 3], values=[1.0, 2.0]]"
        new SparseDoubleVector(6, [2, 5] as int[], [1.5d, 2.75d] as double[]).toString() ==
            "SparseDoubleVector[length=6, indices=[2, 5], values=[1.5, 2.75]]"
    }

    void "sparse vectors defensively copy input arrays"() {
        given:
        int[] indices = [1, 3] as int[]
        byte[] bytes = [5, 9] as byte[]
        float[] floats = [1f, 2f] as float[]
        double[] doubles = [1d, 2d] as double[]

        when:
        def b = new SparseByteVector(4, indices, bytes)
        def f = new SparseFloatVector(4, indices, floats)
        def d = new SparseDoubleVector(4, indices, doubles)
        indices[0] = 0
        bytes[0] = 0
        floats[0] = 0f
        doubles[0] = 0d

        then:
        b.indices().toList() == [1, 3]
        b.values().toList() == [5, 9]
        f.indices().toList() == [1, 3]
        f.values().toList() == [1f, 2f]
        d.indices().toList() == [1, 3]
        d.values().toList() == [1d, 2d]
    }

    void "sparse vector accessors return defensive copies"() {
        given:
        def sparse = new SparseDoubleVector(5, [1, 4] as int[], [1.5d, 3.5d] as double[])

        when:
        int[] indices = sparse.indices()
        double[] values = sparse.values()
        indices[0] = 0
        values[0] = 0d

        then:
        sparse.indices().toList() == [1, 4]
        sparse.values().toList() == [1.5d, 3.5d]
    }

    void "fromDense factory methods preserve type and dense roundtrip"() {
        expect:
        SparseByteVector.fromDense([0, 2, 0, 4] as byte[]).toByteArray().toList() == [0, 2, 0, 4]
        SparseFloatVector.fromDense([0f, 1.5f, 0f, 3.5f] as float[]).toFloatArray().toList() == [0f, 1.5f, 0f, 3.5f]
        SparseDoubleVector.fromDense([0d, 1.25d, 0d, 3.75d] as double[]).toDoubleArray().toList() == [0d, 1.25d, 0d, 3.75d]
        SparseByteVector.fromDense(Vector.of([0 as byte, 8 as byte, 0 as byte] as byte[])).toByteArray().toList() == [0, 8, 0]
        SparseFloatVector.fromDense(Vector.of([0f, 8f, 0f] as float[])).toFloatArray().toList() == [0f, 8f, 0f]
        SparseDoubleVector.fromDense(Vector.of([0d, 8d, 0d] as double[])).toDoubleArray().toList() == [0d, 8d, 0d]
    }

    void "sparse vectors expose numeric type"() {
        expect:
        new SparseByteVector(2, [1] as int[], [1] as byte[]).type == Byte.TYPE
        new SparseFloatVector(2, [1] as int[], [1f] as float[]).type == Float.TYPE
        new SparseDoubleVector(2, [1] as int[], [1d] as double[]).type == Double.TYPE
    }

    void "sparse vectors support equals and hashCode by content"() {
        expect:
        new SparseByteVector(3, [1] as int[], [7] as byte[]) == new SparseByteVector(3, [1] as int[], [7] as byte[])
        new SparseByteVector(3, [1] as int[], [7] as byte[]).hashCode() == new SparseByteVector(3, [1] as int[], [7] as byte[]).hashCode()
        new SparseFloatVector(3, [1] as int[], [7f] as float[]) == new SparseFloatVector(3, [1] as int[], [7f] as float[])
        new SparseFloatVector(3, [1] as int[], [7f] as float[]).hashCode() == new SparseFloatVector(3, [1] as int[], [7f] as float[]).hashCode()
        new SparseDoubleVector(3, [1] as int[], [7d] as double[]) == new SparseDoubleVector(3, [1] as int[], [7d] as double[])
        new SparseDoubleVector(3, [1] as int[], [7d] as double[]).hashCode() == new SparseDoubleVector(3, [1] as int[], [7d] as double[]).hashCode()
        new SparseByteVector(3, [1] as int[], [7] as byte[]) != new SparseByteVector(3, [2] as int[], [7] as byte[])
        new SparseFloatVector(3, [1] as int[], [7f] as float[]) != new SparseFloatVector(4, [1] as int[], [7f] as float[])
        new SparseDoubleVector(3, [1] as int[], [7d] as double[]) != new SparseDoubleVector(3, [1] as int[], [8d] as double[])
    }

    void "sparse vector constructors reject null arrays"() {
        when:
        new SparseByteVector(2, null, [1] as byte[])

        then:
        thrown(NullPointerException)

        when:
        new SparseFloatVector(2, [1] as int[], null)

        then:
        thrown(NullPointerException)

        when:
        new SparseDoubleVector(2, null, [1d] as double[])

        then:
        thrown(NullPointerException)
    }
}
