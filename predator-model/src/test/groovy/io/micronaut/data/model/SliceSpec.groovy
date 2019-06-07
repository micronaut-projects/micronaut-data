package io.micronaut.data.model

import spock.lang.Specification

class SliceSpec extends Specification {

    void "test mapping a slice"() {
        def slice = Slice.of([1, 2, 3, 4, 5], Pageable.from(0, 5))

        when:
        Slice newSlice = slice.map({ i -> i + 1 })

        then:
        newSlice.content == [2,3,4,5,6]
        newSlice.size == 5
    }
}
