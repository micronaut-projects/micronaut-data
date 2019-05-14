package io.micronaut.data.model

import spock.lang.Specification
import spock.lang.Unroll

class PageSpec extends Specification {

    @Unroll
    void "test page for page number #number and size #size"() {
        given:
        def page = Page.of([1, 2, 3, 4, 5], Pageable.from(number, size), total)

        expect:
        page.pageNumber == number
        page.size == size
        page.offset == offset
        page.totalSize == total
        page.totalPages == pages
        page.getNumberOfElements() == 5
        page.nextPageable().size == size
        page.nextPageable().offset == offset + size

        where:
        number | offset | size | total | pages
        0      | 0      | 5    | 14    | 3
        1      | 20     | 20   | 140   | 7
        2      | 40     | 20   | 140   | 7
    }
}
