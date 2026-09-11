package io.micronaut.data.nitrite.runtime.read

import org.dizitart.no2.collection.FindOptions
import org.dizitart.no2.common.SortOrder
import spock.lang.Specification
import spock.lang.Unroll

class CursorWindowSpec extends Specification {

    @Unroll
    void "sorted limit is #action for the selected backend"() {
        given:
        def options = FindOptions.orderBy("age", SortOrder.Ascending).limit(10)

        when:
        def cursorLimit = CursorWindow.withholdSortedLimit(options, useCursorLimit)

        then:
        cursorLimit == expectedCursorLimit
        options.limit() == expectedFindLimit

        where:
        action       | useCursorLimit | expectedCursorLimit | expectedFindLimit
        "withheld"   | true           | 10L                 | null
        "kept"       | false          | -1L                 | 10L
    }
}
