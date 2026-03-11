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

    void "sparse byte vector converts to dense byte array"() {
        given:
        def sparse = new SparseByteVector(5, [0, 4] as int[], [1, 9] as byte[])

        expect:
        sparse.toByteArray().toList() == [1, 0, 0, 0, 9]
    }
}
