package io.micronaut.data.model.vector

import spock.lang.Specification

class BinaryVectorSpec extends Specification {

    void "packs bits eight per byte, most significant bit first"() {
        when:
        BinaryVector vector = BinaryVector.of(true, false, true, false, false, true, false, true,
                false, false, false, false, false, false, false, true)

        then:
        vector.data() == [(byte) 0xA5, (byte) 0x01] as byte[]
        vector.dimensions() == 16
        vector.get(0)
        !vector.get(1)
        vector.get(15)
        vector.toBooleanArray().toList() == [true, false, true, false, false, true, false, true,
                                             false, false, false, false, false, false, false, true]
        vector.toString() == 'b1010010100000001'
    }

    void "array conversions return one element per dimension"() {
        given:
        BinaryVector vector = new BinaryVector([(byte) 0x81] as byte[])

        expect:
        vector.toByteArray().toList() == [1, 0, 0, 0, 0, 0, 0, 1] as List<Byte>
        vector.toFloatArray().toList() == [1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f]
        vector.toDoubleArray().toList() == [1d, 0d, 0d, 0d, 0d, 0d, 0d, 1d]
        vector.type == Byte.TYPE
        vector.toSparseFloatVector().indices() == [0, 7] as int[]
    }

    void "copies its data and compares by value"() {
        given:
        byte[] data = [(byte) 0xF0] as byte[]
        BinaryVector vector = new BinaryVector(data)

        when:
        data[0] = 0
        byte[] returned = vector.data()
        returned[0] = 0

        then:
        vector.data() == [(byte) 0xF0] as byte[]
        vector == new BinaryVector([(byte) 0xF0] as byte[])
        vector.hashCode() == new BinaryVector([(byte) 0xF0] as byte[]).hashCode()
        vector != new BinaryVector([(byte) 0x0F] as byte[])
        vector != Vector.of([(byte) 0xF0] as byte[])
    }

    void "rejects a bit count that is not a multiple of 8"() {
        when:
        BinaryVector.of(true, false, true)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('multiple of 8')
    }

    void "rejects an index outside the dimensions"() {
        when:
        new BinaryVector([(byte) 1] as byte[]).get(8)

        then:
        thrown(IndexOutOfBoundsException)
    }
}
